package org.ecommerce.external.web.api.common.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val status: Status,
    val data: T? = null,
    val message: String? = null,
    val error: ErrorDetail? = null
) {
    enum class Status {
        SUCCESS, ERROR
    }

    companion object {

        fun <T> success(data: T?): ApiResponse<T> =
            ApiResponse(Status.SUCCESS, data = data)

        fun error(
            message: String,
            errorDetail: ErrorDetail? = null
        ): ApiResponse<Nothing> =
            ApiResponse(Status.ERROR, message = message, error = errorDetail)
    }
}
