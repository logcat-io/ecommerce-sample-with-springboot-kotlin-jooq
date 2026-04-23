package org.ecommerce.core.timedeal.exception

import org.ecommerce.core.common.exception.BusinessException

// HTTP 400 — 타임딜이 활성 상태가 아님 (아직 시작 전 / 이미 종료 / 비활성).
// reason 파라미터로 구체적 사유를 전달하여 클라이언트 디버깅 용이.
class TimeDealNotActiveException(reason: String) : BusinessException(
    errorCode = "TIME_DEAL_NOT_ACTIVE",
    httpStatus = 400,
    message = "Time deal is not active: $reason",
)
