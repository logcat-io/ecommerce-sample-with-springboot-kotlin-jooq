package org.ecommerce.core.timedeal.service

import org.ecommerce.core.timedeal.port.StockPort
import java.util.UUID

// ─────────────────────────────────────────────────────────
// 왜 롤백을 한 곳에서 관리하는가?
// ─────────────────────────────────────────────────────────
// Redis 차감 후 실패가 발생하는 지점이 여러 곳이다:
//   - DB Optimistic Lock 실패
//   - UNIQUE 제약 위반
//   - 예상치 못한 런타임 예외
//
// 각 실패 지점에서 직접 stockPort.increase() 를 호출하면:
//   1. 롤백 누락 위험 — 새로운 실패 경로를 추가할 때 잊을 수 있음
//   2. 로깅/메트릭 수집이 분산됨
//   3. 롤백 실패 시 알림 로직을 여러 곳에 중복 작성
//
// 이 클래스를 경유하면 "롤백은 반드시 여기를 통과한다" 는 불변식이 생긴다.
// 코드 리뷰에서 rollbackHandler.rollback() 호출 여부만 확인하면 된다.
// ─────────────────────────────────────────────────────────
class StockRollbackHandler(
    private val stockPort: StockPort
) {
    fun rollback(timeDealId: UUID, quantity: Int) {
        // 롤백 실패 시에도 예외를 삼키지 않는다.
        // 여기서 실패하면 Redis 와 DB 의 재고가 불일치 — 수동 개입 필요.
        // 실무에서는 이 지점에 모니터링 알림(Slack/PagerDuty)을 반드시 붙인다.
        stockPort.increase(timeDealId, quantity)
    }
}
