package org.ecommerce.application.product.dto

import java.util.*

data class SearchProductQuery(
    val category: String,
    val cursor: UUID?,
    val size: Int,
)
