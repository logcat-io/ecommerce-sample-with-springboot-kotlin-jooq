package org.ecommerce.core.product.port

import org.ecommerce.core.product.model.Product
import java.util.*

interface ProductQueryPort {

    fun findById(id: UUID): Product?

    fun findByCategory(
        category: String,
        cursor: UUID?,
        size: Int
    ): List<Product>
}
