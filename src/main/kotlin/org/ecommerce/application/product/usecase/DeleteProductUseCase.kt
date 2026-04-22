package org.ecommerce.application.product.usecase

import org.ecommerce.core.common.exception.ProductNotFoundException
import org.ecommerce.core.product.port.ProductCommandPort
import org.ecommerce.core.product.port.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class DeleteProductUseCase(
    private val productCommandPort: ProductCommandPort,
    private val productQueryPort: ProductQueryPort
) {
    @Transactional
    fun execute(id: UUID) {
        productQueryPort.findById(id) ?: throw ProductNotFoundException(id)
        productCommandPort.delete(id)
    }
}
