package org.ecommerce.external.web.api.controller.v1.product.request

import org.ecommerce.application.product.dto.CreateProductDto
import java.math.BigDecimal

data class CreateProductRequest(
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
){
    fun toCommand() = CreateProductDto(
        name = name,
        description = description,
        price = price,
        category = category,
    )
}
