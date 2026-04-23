package org.ecommerce.core.timedeal.port

import org.ecommerce.core.timedeal.model.TimeDeal
import java.util.*

interface TimeDealQueryPort {

    // 타임딜 단건 조회. 없으면 null.
    fun findById(timeDealId: UUID): TimeDeal?

    // 해당 사용자의 누적 구매 수량 조회. 구매 이력 없으면 0.
    // 선검사 용도 — 최종 방어는 savePurchaseRecord 의 WHERE 조건이 담당.
    fun getPurchasedQuantity(timeDealId: UUID, userId: UUID): Int

    fun findCurrentVersion(timeDealId: UUID): Long?

    fun findAllActive(): List<TimeDeal>
}
