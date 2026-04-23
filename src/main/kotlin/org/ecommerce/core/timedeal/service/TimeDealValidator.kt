package org.ecommerce.core.timedeal.service

import org.ecommerce.core.timedeal.model.TimeDeal
import java.time.Instant

// ─────────────────────────────────────────────────────────
// 순수 검증 로직. 부수 효과(side effect) 없음.
// ─────────────────────────────────────────────────────────
// 왜 예외를 던지지 않고 enum 을 반환하는가?
//   1. 테스트에서 assertThat(result).isEqualTo(Check.ENDED) 처럼 명시적 검증 가능.
//   2. 호출자(UseCase)가 실패 사유별로 다른 예외를 던질 수 있다.
//   3. 검증 로직 자체는 "판단" 만 하고, "반응" 은 호출자가 결정 — SRP. -> Ditto: 매우 좋은 개발 철학
// ─────────────────────────────────────────────────────────
class TimeDealValidator {

    enum class Check {
        OK,
        NOT_STARTED,
        ENDED,
        INACTIVE,
        QUANTITY_INVALID,
    }

    fun check(deal: TimeDeal, quantity: Int, now: Instant): Check =
        when {
            // 수량 검증 - 최우선
            quantity < 1 -> Check.QUANTITY_INVALID
            quantity > deal.maxPerUser -> Check.QUANTITY_INVALID

            // 시간 검증 - 스케줄러 상태 전환 지연을 커버
            now.isBefore(deal.startAt) -> Check.NOT_STARTED
            now.isAfter(deal.endAt) -> Check.ENDED

            // 상태 검증 - isActiveAt 이 시간 + 상태 모두 확인
            !deal.isActiveAt(now) -> Check.INACTIVE

            else -> Check.OK
        }
}
