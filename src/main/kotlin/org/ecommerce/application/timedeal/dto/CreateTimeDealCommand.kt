package org.ecommerce.application.timedeal.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateTimeDealCommand(
    val productId: UUID,
    val dealPrice: BigDecimal,
    val originalPrice: BigDecimal,
    val totalStock: Int,
    val maxPerUser: Int,
    val startAt: Instant,
    val endAt: Instant,
)
