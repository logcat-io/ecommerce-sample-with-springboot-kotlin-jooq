package org.ecommerce.application.product.dto

import java.math.BigDecimal
import java.util.*

data class UpdateProductDto(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
)
