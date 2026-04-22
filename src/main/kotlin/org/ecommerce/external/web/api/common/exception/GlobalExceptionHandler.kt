package org.ecommerce.external.web.api.common.exception

import org.ecommerce.core.common.exception.BusinessException
import org.ecommerce.external.web.api.common.response.ApiResponse
import org.ecommerce.external.web.api.common.response.ErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Business exception: [{}] {}", ex.errorCode, ex.message)
        return ResponseEntity
            .status(ex.httpStatus)
            .body(
                ApiResponse.error(
                    message = ex.message,
                    errorDetail = ErrorDetail(code = ex.errorCode),
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Illegal argument: {}", ex.message)
        return ResponseEntity
            .badRequest()
            .body(
                ApiResponse.error(
                    message = ex.message ?: "잘못된 요청입니다",
                    errorDetail = ErrorDetail(code = "BAD_REQUEST"),
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error", ex)
        return ResponseEntity
            .internalServerError()
            .body(
                ApiResponse.error(
                    message = "서버 내부 오류가 발생했습니다",
                    errorDetail = ErrorDetail(code = "INTERNAL_ERROR"),
                )
            )
    }
}
