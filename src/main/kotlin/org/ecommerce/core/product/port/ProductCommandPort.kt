package org.ecommerce.core.product.port

import org.ecommerce.core.product.domain.Product
import java.util.UUID

interface ProductCommandPort {
    fun save(product: Product): Product
    fun delete(id: UUID)
}
