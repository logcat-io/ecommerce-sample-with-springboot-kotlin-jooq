package org.ecommerce.external.persistence.jooq.product

import org.ecommerce.core.product.domain.Product
import org.ecommerce.core.product.domain.ProductStatus
import org.ecommerce.core.product.port.ProductCommandPort
import org.ecommerce.core.product.port.ProductQueryPort
import org.ecommerce.jooq.generated.tables.records.ProductsRecord
import org.ecommerce.jooq.generated.tables.references.PRODUCTS
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetStep
import org.jooq.InsertSetStep
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.*

@Repository
class ProductJooqAdapter(
    private val dsl: DSLContext
) : ProductCommandPort, ProductQueryPort {

    private fun insertFieldsFor(
        product: Product,
        q: InsertSetStep<ProductsRecord>
    ) = q
        .set(PRODUCTS.ID, product.id)
        .set(PRODUCTS.NAME, product.name)
        .set(PRODUCTS.DESCRIPTION, product.description)
        .set(PRODUCTS.PRICE, product.price)
        .set(PRODUCTS.CATEGORY, product.category)
        .set(PRODUCTS.STATUS, product.status.name)
        .set(PRODUCTS.CREATED_AT, product.createdAt.atOffset(ZoneOffset.UTC))
        .set(PRODUCTS.UPDATED_AT, product.updatedAt.atOffset(ZoneOffset.UTC))

    private fun updateFieldsFor(
        product: Product,
        q: InsertOnDuplicateSetStep<ProductsRecord>
    ) = q
        .set(PRODUCTS.NAME, product.name)
        .set(PRODUCTS.DESCRIPTION, product.description)
        .set(PRODUCTS.PRICE, product.price)
        .set(PRODUCTS.CATEGORY, product.category)
        .set(PRODUCTS.STATUS, product.status.name)
        .set(PRODUCTS.UPDATED_AT, product.updatedAt.atOffset(ZoneOffset.UTC))

    override fun save(product: Product): Product {
        dsl.insertInto(PRODUCTS)
            .let { insertFieldsFor(product, it) }
            .onConflict(PRODUCTS.ID)
            .doUpdate()
            .let { updateFieldsFor(product, it) }
            .execute()

        return product
    }

    override fun delete(id: UUID) {
        dsl.deleteFrom(PRODUCTS)
            .where(PRODUCTS.ID.eq(id))
            .execute()
    }

    override fun findById(id: UUID): Product? =
        dsl.selectFrom(PRODUCTS)
            .where(PRODUCTS.ID.eq(id))
            .fetchOne { it.toDomain() }

    override fun findByCategory(
        category: String,
        cursor: UUID?,
        size: Int
    ): List<Product> {

        val base = PRODUCTS.CATEGORY.eq(category)
            .and(PRODUCTS.STATUS.eq(ProductStatus.ACTIVE.name))

        val condition = if (cursor != null) {
            base.and(PRODUCTS.ID.lt(cursor))
        } else {
            base
        }

        return dsl.selectFrom(PRODUCTS)
            .where(condition)
            .orderBy(PRODUCTS.ID.desc())
            .limit(size)
            .fetch { it.toDomain() }
    }

    private fun ProductsRecord.toDomain(): Product =
        Product(
            id = this.id!!,
            name = this.name!!,
            description = this.description,
            price = this.price!!,
            category = this.category!!,
            status = ProductStatus.valueOf(this.status!!),
            createdAt = this.createdAt!!.toInstant(),
            updatedAt = this.updatedAt!!.toInstant(),
        )
}
