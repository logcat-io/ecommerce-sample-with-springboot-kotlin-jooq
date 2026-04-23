package org.ecommerce.application.timedeal.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.*

class CreateTimeDealResult(
    val id: UUID,
    val productId: UUID,
    val dealPrice: BigDecimal,
    val totalStock: Int,
    val status: String,
    val startAt: Instant,
    val endAt: Instant,
)
