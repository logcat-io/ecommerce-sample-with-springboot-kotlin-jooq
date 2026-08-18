package org.ecommerce.application.timedeal.usecase

import org.ecommerce.application.timedeal.dto.PurchaseTimeDealCommand
import org.ecommerce.application.timedeal.dto.PurchaseTimeDealResult
import org.ecommerce.core.timedeal.exception.PurchaseLimitExceededException
import org.ecommerce.core.timedeal.exception.StockExhaustedException
import org.ecommerce.core.timedeal.exception.StockVersionConflictException
import org.ecommerce.core.timedeal.exception.TimeDealNotActiveException
import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.ecommerce.core.timedeal.service.StockDecreaser
import org.ecommerce.core.timedeal.service.StockRollbackHandler
import org.ecommerce.core.timedeal.service.TimeDealValidator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// ═══════════════════════════════════════════════════════════
// PurchaseTimeDealUseCase — 이 프로젝트의 핵심 동시성 흐름.
// ═══════════════════════════════════════════════════════════
//
// 이 클래스를 읽으면 "구매 요청이 어떤 순서로 처리되는가?" 가
// 위에서 아래로 그대로 보여야 한다.
// UseCase 는 흐름 오케스트레이션만 담당하고,
// 실제 비즈니스 로직은 도메인 서비스에 위임한다.
//
// 흐름:
//   Step 0: Redis 재고 확인 — 확정 실패를 DB 접근 전에 거른다
//   Step 1: 딜 존재 + 활성 검증
//   Step 2: 재고 차감 (Redis → DB)
//   Step 3: 구매 이력 UPSERT (maxPerUser 원자 최종 방어)
//
// 모든 실패 경로에서 Redis 재고가 반드시 복구되어야 한다.
// ═══════════════════════════════════════════════════════════
@Service
class PurchaseTimeDealUseCase(
    private val timeDealQueryPort: TimeDealQueryPort,
    private val timeDealCommandPort: TimeDealCommandPort,
    private val stockPort: StockPort,
    private val stockDecreaser: StockDecreaser,
    private val rollbackHandler: StockRollbackHandler,
    private val validator: TimeDealValidator
) {
    // @Transactional 경계:
    //   - DB 기록 (decreaseStockWithVersion + savePurchaseRecord) 이 하나의 트랜잭션.
    //   - Redis 는 트랜잭션 밖의 외부 호출이므로 보상 기반으로 일관성 유지.
    //   - 트랜잭션 롤백 시 DB 변경은 자동 취소되지만, Redis 는 수동 롤백 필요.

    @Transactional
    fun execute(
        command: PurchaseTimeDealCommand
    ): PurchaseTimeDealResult {
        val now = Instant.now()

        // Step 0: 재고가 이미 소진됐으면 여기서 끝낸다.
        // 차감이 아니라 확인이라 실패해도 되돌릴 것이 없다.
        // 정확한 게이트는 Step 2 의 Lua 가 원자적으로 잡는다.
        if (stockPort.getRemaining(command.timeDealId) < command.quantity) {
            throw StockExhaustedException()
        }

        // Step 1: 딜 확인 및 활성화 검증
        val deal = timeDealQueryPort.findById(command.timeDealId)
            ?: throw TimeDealNotActiveException("Time Deal not found")

        // validation
        when(validator.check(deal, command.quantity, now)) {
            TimeDealValidator.Check.OK -> Unit
            TimeDealValidator.Check.NOT_STARTED -> throw TimeDealNotActiveException("Time Deal is not started")
            TimeDealValidator.Check.ENDED -> throw TimeDealNotActiveException("Time Deal is ended")
            TimeDealValidator.Check.INACTIVE -> throw TimeDealNotActiveException("Time Deal is in active")
            TimeDealValidator.Check.QUANTITY_INVALID -> throw TimeDealNotActiveException("Time Deal quantity is invalid")
        }

        // Step 2: 재고 차감 (Redis 원자 + DB Optimistic Lock)
        when(val result = stockDecreaser.decrease(deal.id, command.quantity)) {
            StockDecreaser.Result.Success -> Unit
            StockDecreaser.Result.StockExhausted -> throw StockExhaustedException()
            is StockDecreaser.Result.VersionConflict -> throw StockVersionConflictException(result.attempts)
        }

        // Step 3: 구매 이력 UPSERT - maxPerUser 원자 최종 방어선
        // 선검사 없이 이 UPSERT 하나로 한도를 보장한다.
        val recorded = timeDealCommandPort.savePurchaseRecord(
            timeDealId = deal.id,
            userId = command.userId,
            quantity = command.quantity,
            maxPerUser = deal.maxPerUser,
        )

        if(!recorded) {
            rollbackHandler.rollback(deal.id, command.quantity)
            throw PurchaseLimitExceededException()
        }

        return PurchaseTimeDealResult(
            timeDealId = deal.id,
            userId = command.userId,
            quantity = command.quantity,
            dealPrice = deal.dealPrice,
            purchasedAt = now,
        )
    }
}
