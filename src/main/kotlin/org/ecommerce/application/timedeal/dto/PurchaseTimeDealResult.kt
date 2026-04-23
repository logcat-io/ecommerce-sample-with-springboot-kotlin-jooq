package org.ecommerce.application.timedeal.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PurchaseTimeDealResult(
    val timeDealId: UUID,
    val userId: UUID,
    val quantity: Int,
    val dealPrice: BigDecimal,
    val purchasedAt: Instant,
)
