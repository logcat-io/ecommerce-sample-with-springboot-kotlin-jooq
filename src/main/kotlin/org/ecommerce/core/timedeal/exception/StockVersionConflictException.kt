package org.ecommerce.core.timedeal.exception

import org.ecommerce.core.common.exception.BusinessException

class StockVersionConflictException(
    val attempts: Int,
) : BusinessException(
    errorCode = "STOCK_VERSION_CONFLICT",
    httpStatus = 409,
    message = "stock decrease failed after $attempts optimistic lock attempts"
)
