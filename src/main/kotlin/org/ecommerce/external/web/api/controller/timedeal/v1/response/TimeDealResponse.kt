package org.ecommerce.external.web.api.controller.timedeal.v1.response

import org.ecommerce.application.timedeal.dto.CreateTimeDealResult
import org.ecommerce.application.timedeal.dto.PurchaseTimeDealResult
import java.math.BigDecimal
import java.util.UUID
import java.time.Instant

data class PurchaseResponse(
    val timeDealId: UUID,
    val quantity: Int,
    val dealPrice: BigDecimal,
    val purchasedAt: Instant,
) {
    companion object {
        fun from(r: PurchaseTimeDealResult) = PurchaseResponse(
            timeDealId = r.timeDealId,
            quantity = r.quantity,
            dealPrice = r.dealPrice,
            purchasedAt = r.purchasedAt,
        )
    }
}

data class CreateTimeDealResponse(
    val id: UUID,
    val productId: UUID,
    val dealPrice: BigDecimal,
    val totalStock: Int,
    val status: String,
    val startAt: Instant,
    val endAt: Instant,
) {
    companion object {
        fun from(r: CreateTimeDealResult) = CreateTimeDealResponse(
            id = r.id,
            productId = r.productId,
            dealPrice = r.dealPrice,
            totalStock = r.totalStock,
            status = r.status,
            startAt = r.startAt,
            endAt = r.endAt,
        )
    }
}
