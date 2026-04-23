package org.ecommerce.core.timedeal.service

import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import java.util.*

// ─────────────────────────────────────────────────────────
// Redis 1차 → DB 2차 차감을 한 곳에서 제어.
// UseCase 는 이 서비스를 호출만 하고 내부 순서를 모른다.
// ─────────────────────────────────────────────────────────
//
// 전체 흐름:
//
//   ┌─ tryDecrease(Redis) ─┐
//   |  재고 >= quantity?    │
//   │  Yes → DECRBY        │  No → return StockExhausted
//   └──────────┬───────────┘
//              ▼
//   ┌─ decreaseStockWithVersion(DB) ─┐
//   │  WHERE version = expected       │
//   │  AND remaining_stock >= qty     │
//   │  affected=1? Success           │  affected=0? → Redis rollback → VersionConflict
//   └──────────┬──────────────────────┘
//              ▼
//         return Success
//
// 예외 발생 시 (DB 커넥션 끊김 등):
//   → Redis rollback → 예외 재throw
//
class StockDecreaser(
    private val stockPort: StockPort,
    private val timeDealCommandPort: TimeDealCommandPort,
    private val timeDealQueryPort: TimeDealQueryPort,
    private val rollbackHandler: StockRollbackHandler,
) {

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    // sealed class 를 사용하는 이유
    // - 예외가 아닌 타입으로 실패를 표현 -> when 에서 컴파일러가 모든 분기를 강제.
    // - UseCase 가 실패 사유별로 다른 HTTP 상태 코드를 매핑할 수 있게 된다.
    sealed class Result {
        object Success : Result()
        object StockExhausted : Result()
        object VersionConflict : Result()
    }

    fun decrease(
        timeDealId: UUID,
        quantity: Int,
    ): Result {
        // 1 단계: Redis 원자 차감
        // Redis Lua 스크립트가 "현재 재고 >= quantity" 를 확인하고 DECRBY 를 원자적으로 수행
        // 실패 시 DB 에 접근하지 않음 -> DB 부하 차단히 핵심 목적
        val redisOk = stockPort.tryDecrease(timeDealId, quantity)
        if(!redisOk) return Result.StockExhausted

        // 2 단계: DB Optimistic Lock Update
        // Redis 를 통과했으므로 "유력 후보" 만 DB 에 도달한다.
        // version 이 일치해야 UPDATE 가 적용된다.

        repeat(MAX_RETRY_COUNT) {
            val currentVersion = timeDealQueryPort.findCurrentVersion(timeDealId)
                ?: run {
                    rollbackHandler.rollback(timeDealId, quantity)
                    return Result.VersionConflict
                }

            val dbOk = try {
                timeDealCommandPort.decreaseStockWithVersion(timeDealId, quantity, currentVersion)

            } catch (e: Exception) {
                // 예상하지 못한 예외가 발생(DB 커넥션 끊김, 타임아웃 등)
                // Redis 는 이미 차감되었으므로 반드시 복구 후 재 throw
                rollbackHandler.rollback(timeDealId, quantity)
                throw e
            }

            if(dbOk) return Result.Success
        }

        rollbackHandler.rollback(timeDealId, quantity)
        return Result.VersionConflict
    }
}
