package org.ecommerce.external.web.api.controller.timedeal.v1

import org.ecommerce.application.timedeal.usecase.CreateTimeDealUseCase
import org.ecommerce.application.timedeal.usecase.PurchaseTimeDealUseCase
import org.ecommerce.external.web.api.common.response.ApiResponse
import org.ecommerce.external.web.api.controller.timedeal.v1.request.CreateTimeDealRequest
import org.ecommerce.external.web.api.controller.timedeal.v1.request.PurchaseRequest
import org.ecommerce.external.web.api.controller.timedeal.v1.response.CreateTimeDealResponse
import org.ecommerce.external.web.api.controller.timedeal.v1.response.PurchaseResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/time-deals")
class TimeDealController(
    private val purchaseTimeDealUseCase: PurchaseTimeDealUseCase,
    private val createTimeDealUseCase: CreateTimeDealUseCase
) {

    @PostMapping("/{id}/purchase")
    fun purchase(
        @PathVariable id: UUID,
        @RequestBody request: PurchaseRequest
    ): ResponseEntity<ApiResponse<PurchaseResponse>> {
        val result = purchaseTimeDealUseCase.execute(request.toCommand(id))
        return ResponseEntity
            .status(201)
            .body(ApiResponse.success(PurchaseResponse.from(result)))
    }

    @PostMapping
    fun create(
        @RequestBody request: CreateTimeDealRequest
    ): ResponseEntity<ApiResponse<CreateTimeDealResponse>> {
        val result = createTimeDealUseCase.execute(request.toCommand())
        return ResponseEntity
            .status(201)
            .body(ApiResponse.success(CreateTimeDealResponse.from(result)))
    }
}
