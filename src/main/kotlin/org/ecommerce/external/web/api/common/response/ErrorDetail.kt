package org.ecommerce.external.web.api.common.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorDetail(
    val code: String,
    val fields: Map<String, String>? = null,
)
