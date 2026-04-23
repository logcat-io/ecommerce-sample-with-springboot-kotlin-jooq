package org.ecommerce.external.web.api.controller.product.v1.response

import org.ecommerce.application.product.dto.ProductResult
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class ProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
    val status: String,
    val createdAt:  Instant,
) {
    companion object {

        fun from(pr: ProductResult) = ProductResponse(
            id = pr.id,
            name = pr.name,
            description = pr.description,
            price = pr.price,
            category = pr.category,
            status = pr.status,
            createdAt = pr.createdAt,
        )
    }
}
