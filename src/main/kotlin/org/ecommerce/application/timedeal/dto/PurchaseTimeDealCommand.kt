package org.ecommerce.application.timedeal.dto

import java.util.UUID

data class PurchaseTimeDealCommand(
    val timeDealId: UUID,
    val userId: UUID,
    val quantity: Int,
)
