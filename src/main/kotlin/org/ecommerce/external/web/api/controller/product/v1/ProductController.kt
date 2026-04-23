package org.ecommerce.external.web.api.controller.product.v1

import org.ecommerce.application.product.dto.SearchProductQuery
import org.ecommerce.application.product.usecase.*
import org.ecommerce.external.web.api.common.response.ApiResponse
import org.ecommerce.external.web.api.controller.product.v1.request.CreateProductRequest
import org.ecommerce.external.web.api.controller.product.v1.request.UpdateProductRequest
import org.ecommerce.external.web.api.controller.product.v1.response.ProductResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val getProductUseCase: GetProductUseCase,
    private val searchProductsUseCase: SearchProductsUseCase
) {

    @GetMapping("/{id}")
    fun getOne(@PathVariable id: UUID): ApiResponse<ProductResponse> {
        val result = getProductUseCase.execute(id)
        return ApiResponse.success(ProductResponse.Companion.from(result))

    }

    @GetMapping
    fun search(
        @RequestParam category: String,
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<ProductResponse>> {
        val capped = size.coerceAtMost(50)
        val results = searchProductsUseCase.execute(
            SearchProductQuery(category, cursor, capped)
        )

        return ApiResponse.success(results.map { ProductResponse.Companion.from(it) })
    }

    @PostMapping
    fun create(@RequestBody request: CreateProductRequest): ResponseEntity<ApiResponse<ProductResponse>> {
        val response = createProductUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(ProductResponse.Companion.from(response)))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateProductRequest): ApiResponse<ProductResponse>{
        val response = updateProductUseCase.execute(request.toCommand(id))
        return ApiResponse.success(ProductResponse.Companion.from(response))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void>{
        deleteProductUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }
}
