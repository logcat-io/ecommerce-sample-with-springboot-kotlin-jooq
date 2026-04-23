package org.ecommerce.application.product.dto

import org.ecommerce.core.product.model.Product
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class ProductResult(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
    val status: String,
    val createdAt: Instant,
) {

    companion object {
        fun from(product: Product) = ProductResult(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            category = product.category,
            status = product.status.name,
            createdAt = product.createdAt,
        )
    }

}
