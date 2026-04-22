package org.ecommerce.application.product.usecase

import org.ecommerce.application.product.dto.ProductResult
import org.ecommerce.application.product.dto.SearchProductQuery
import org.ecommerce.core.product.port.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchProductsUseCase(
    private val productQueryPort: ProductQueryPort
) {

    @Transactional(readOnly = true)
    fun execute(query: SearchProductQuery): List<ProductResult> =
        productQueryPort
            .findByCategory(query.category, query.cursor, query.size)
            .map { ProductResult.from(it) }
}
