package org.ecommerce.core.timedeal.exception

import org.ecommerce.core.common.exception.BusinessException

// HTTP 409 Conflict — 재고 소진.
// 왜 409 인가?
//   - 400 은 "요청 형식이 잘못됨" — 재고 소진은 요청 자체는 올바름.
//   - 409 는 "현재 리소스 상태와 충돌" — 재고가 0 이라는 상태 충돌.
//   - 클라이언트는 409 를 보면 "재시도해도 의미 없음" 을 알 수 있다.
class StockExhaustedException : BusinessException(
    errorCode = "STOCK_EXHAUSTED",
    httpStatus = 409,
    message = "Time deal stock exhausted",
)
