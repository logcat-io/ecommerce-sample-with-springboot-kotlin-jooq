package org.ecommerce.application.product.usecase

import org.ecommerce.application.product.dto.CreateProductDto
import org.ecommerce.application.product.dto.ProductResult
import org.ecommerce.core.product.domain.Product
import org.ecommerce.core.product.port.ProductCommandPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateProductUseCase(
    private val productCommandPort: ProductCommandPort
) {
    @Transactional
    fun execute(command: CreateProductDto): ProductResult {
        val product = Product.create(
            name = command.name,
            description = command.description,
            price = command.price,
            category = command.category,
        )

        val saved = productCommandPort.save(product)

        return ProductResult.from(saved)

    }
}
