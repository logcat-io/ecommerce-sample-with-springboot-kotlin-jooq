package org.ecommerce.core.common.exception

import java.util.*

class ProductNotFoundException(
    id: UUID
): BusinessException(
    errorCode = "PRODUCT_NOT_FOUND",
    httpStatus = 404,
    message = "Product with id: $id not found"
)
