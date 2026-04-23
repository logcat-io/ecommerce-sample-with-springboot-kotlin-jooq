package org.ecommerce.core.product.model

import com.fasterxml.uuid.Generators
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 *
 */
data class Product(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val category: String,
    val status: ProductStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        // invariant
        // IllegalArgumentException 발생
        require(name.isNotBlank()) { "Product name cannot be blank" }
        require(category.isNotBlank()) { "Product category cannot be blank" }
        require(price > BigDecimal.ZERO) { "Product price must be positive" }
    }

    fun deactivate(now: Instant): Product =
        copy(status = ProductStatus.INACTIVE, updatedAt = now)

    fun updateDetails(
        name: String,
        description: String?,
        price: BigDecimal,
        category: String,
        now: Instant = Instant.now(),
    ): Product = copy(
        name = name,
        description = description,
        price = price,
        category = category,
        status = ProductStatus.ACTIVE,
        updatedAt = now,
    )
    // copy 내부에서 init {} 블록이 다시 실행된다.

    companion object {

        private val uuidGenerator = Generators.timeBasedEpochGenerator()

        fun create(
            name: String,
            description: String?,
            price: BigDecimal,
            category: String,
            now: Instant = Instant.now()
        ): Product = Product(
            id = uuidGenerator.generate(),
            name = name,
            description = description,
            price = price,
            category = category,
            status = ProductStatus.INACTIVE,
            createdAt = now,
            updatedAt = now
        )
    }
}
