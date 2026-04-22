package org.ecommerce.application.product.usecase

import org.ecommerce.application.product.dto.ProductResult
import org.ecommerce.core.common.exception.ProductNotFoundException
import org.ecommerce.core.product.port.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetProductUseCase(
    private val productQueryPort: ProductQueryPort
) {

    @Transactional(readOnly = true)
    fun execute(id: UUID): ProductResult {
        val product = productQueryPort.findById(id)
            ?: throw ProductNotFoundException(id)

        return ProductResult.from(product)
    }

}
