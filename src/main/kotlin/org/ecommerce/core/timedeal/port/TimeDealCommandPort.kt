package org.ecommerce.core.timedeal.port

import org.ecommerce.core.timedeal.model.TimeDeal
import java.util.UUID

interface TimeDealCommandPort {

    // 타임딜 저장 (upsert).
    fun save(deal: TimeDeal): TimeDeal

    // ─────────────────────────────────────────────────────────
    // Optimistic Lock 기반 재고 차감.
    // ─────────────────────────────────────────────────────────
    // SQL:
    //   UPDATE time_deals
    //   SET remaining_stock = remaining_stock - ?,
    //       version = version + 1
    //   WHERE id = ?
    //     AND version = ?              ← 읽었을 때의 version 과 같아야 함
    //     AND remaining_stock >= ?     ← 음수 방지
    //
    // 반환: true (affected=1) / false (affected=0 = 경합 패배)
    //
    // 왜 SELECT 해서 남은 수량 체크 후 UPDATE 하지 않는가?
    //   → SELECT 와 UPDATE 사이에 다른 트랜잭션이 끼어들 수 있다 (TOCTOU).
    //   → 한 번의 UPDATE 문에서 조건 + 갱신을 동시에 수행해야 원자적이다.
    // ─────────────────────────────────────────────────────────
    fun decreaseStockWithVersion(
        id: UUID,
        quantity: Int,
        expectedVersion: Long,
    ): Boolean

    // 구매 이력 UPSERT.
    //
    // 첫 구매: INSERT (time_deal_id, user_id, quantity)
    // 재구매:  ON CONFLICT DO UPDATE SET quantity += ? WHERE quantity + ? <= maxPerUser
    //
    // 반환:
    //   true  — affected=1: 삽입 또는 수량 누적 성공
    //   false — affected=0: WHERE 조건 실패, maxPerUser 한도 초과
    //
    // SELECT 없이 단일 SQL 로 check-and-increment 가 원자적으로 처리된다.
    fun savePurchaseRecord(timeDealId: UUID, userId: UUID, quantity: Int, maxPerUser: Int): Boolean
}
