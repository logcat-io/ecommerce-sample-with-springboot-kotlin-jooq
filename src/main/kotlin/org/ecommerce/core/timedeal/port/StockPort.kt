package org.ecommerce.core.timedeal.port

import java.util.*

// ─────────────────────────────────────────────────────────────
// 왜 Redis 를 Port 뒤에 숨기는가?
// ─────────────────────────────────────────────────────────────
// 도메인(domain/ 패키지)은 "재고를 원자적으로 차감할 수 있는 무언가" 만 알면 된다.
// 그것이 Redis 인지, Memcached 인지, 심지어 DB FOR UPDATE 인지는
// 인프라(infrastructure 모듈)의 결정이다.
//
// 이렇게 하면:
//   1. domain/ 패키지에 Redis 의존성이 0 → 순수 Kotlin 유닛 테스트 가능
//   2. Redis 가 죽었을 때 DB fallback Adapter 로 교체 가능 (코드 변경 0)
//   3. 테스트에서 InMemoryStockPort 로 대체하여 빠른 검증 가능
// ─────────────────────────────────────────────────────────────
interface StockPort {

    // 원자 차감.
    // 성공(true): 수량만큼 감소 완료.
    // 실패(false): 재고 부족 — 변경 없음 (원자적 check-and-decrement).
    //
    // 구현체(StockRedisAdapter) 는 Lua 스크립트로 이것을 보장한다.
    fun tryDecrease(timeDealId: UUID, quantity: Int): Boolean

    // 실패 보상용 복구.
    // DB Optimistic Lock 실패 / savePurchaseRecord 한도 초과 시 Redis 재고를 되돌린다.
    fun increase(timeDealId: UUID, quantity: Int)

    // 현재 남은 재고 조회. 모니터링/디버깅/테스트 검증용.
    fun getRemaining(timeDealId: UUID): Int

    // 타임딜 시작 시 DB 의 total_stock 값을 Redis 에 세팅.
    // 스케줄러 또는 딜 생성 직후 호출.
    fun initialize(timeDealId: UUID, stock: Int)
}
