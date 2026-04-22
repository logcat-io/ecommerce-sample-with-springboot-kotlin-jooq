package org.ecommerce.application.product.usecase

import org.ecommerce.application.product.dto.ProductResult
import org.ecommerce.application.product.dto.UpdateProductDto
import org.ecommerce.core.common.exception.ProductNotFoundException
import org.ecommerce.core.product.port.ProductCommandPort
import org.ecommerce.core.product.port.ProductQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateProductUseCase(
    private val productCommandPort: ProductCommandPort,
    private val productQueryPort: ProductQueryPort
) {

    @Transactional
    fun execute(command: UpdateProductDto): ProductResult {
        val existing = productQueryPort.findById(command.id)
            ?: throw ProductNotFoundException(command.id)

        val updated = existing.updateDetails(
            name = command.name,
            description = command.description,
            category = command.category,
            price = command.price,
        )

        return ProductResult.from(productCommandPort.save(updated))
    }
}
