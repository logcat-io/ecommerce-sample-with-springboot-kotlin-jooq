package org.ecommerce.external.web.api.controller.timedeal.v1.request

import org.ecommerce.application.timedeal.dto.CreateTimeDealCommand
import org.ecommerce.application.timedeal.dto.PurchaseTimeDealCommand
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class PurchaseRequest(
    val userId: UUID,
    val quantity: Int,
) {
    fun toCommand(timeDealId: UUID) = PurchaseTimeDealCommand(
        timeDealId = timeDealId,
        userId = userId,
        quantity = quantity
    )
}

data class CreateTimeDealRequest(
    val productId: UUID,
    val dealPrice: BigDecimal,
    val originalPrice: BigDecimal,
    val totalStock: Int,
    val maxPerUser: Int,
    val startAt: Instant,
    val endAt: Instant,
) {
    fun toCommand() = CreateTimeDealCommand(
        productId = productId,
        dealPrice = dealPrice,
        originalPrice = originalPrice,
        totalStock = totalStock,
        maxPerUser = maxPerUser,
        startAt = startAt,
        endAt = endAt,
    )
}
