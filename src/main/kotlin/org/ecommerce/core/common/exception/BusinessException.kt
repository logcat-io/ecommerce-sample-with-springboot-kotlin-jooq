package org.ecommerce.core.common.exception

abstract class BusinessException(
    val errorCode: String,
    override val message: String,
    val httpStatus: Int = 400,
) : RuntimeException(message)
