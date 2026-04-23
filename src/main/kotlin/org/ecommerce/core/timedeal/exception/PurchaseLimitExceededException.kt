package org.ecommerce.core.timedeal.exception

import org.ecommerce.core.common.exception.BusinessException

// HTTP 409 — 1인 구매 제한 초과.
class PurchaseLimitExceededException : BusinessException(
    errorCode = "PURCHASE_LIMIT_EXCEEDED",
    httpStatus = 409,
    message = "Purchase limit per user exceeded",
)
