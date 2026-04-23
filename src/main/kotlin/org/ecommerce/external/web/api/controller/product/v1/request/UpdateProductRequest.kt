package org.ecommerce.external.web.api.controller.product.v1.request

import org.ecommerce.application.product.dto.UpdateProductDto
import java.math.BigDecimal
import java.util.UUID

data class UpdateProductRequest(
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
){
    fun toCommand(id: UUID) = UpdateProductDto(
        id = id,
        name = name,
        description = description,
        price = price,
        category = category,
    )
}
