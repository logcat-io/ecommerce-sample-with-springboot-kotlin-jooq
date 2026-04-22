package org.ecommerce.application.product.dto

import java.math.BigDecimal

data class CreateProductDto(
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
)
