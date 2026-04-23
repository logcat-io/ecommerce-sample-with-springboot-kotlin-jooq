package org.ecommerce.core.timedeal.service

import org.ecommerce.core.timedeal.model.TimeDeal
import org.ecommerce.core.timedeal.model.TimeDealStatus
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

// ─────────────────────────────────────────────────────────
// 프레임워크 의존 0 — JUnit + Kotlin 표준 라이브러리만 사용.
// Spring Context 를 띄우지 않으므로 0.01초 안에 실행된다.
// ─────────────────────────────────────────────────────────
class TimeDealValidatorTest {

    private val validator = TimeDealValidator()
    private val now = Instant.now()

    // 테스트용 기본 딜 생성 헬퍼.
    private fun activeDeal(
        startAt: Instant = now.minus(1, ChronoUnit.HOURS),
        endAt: Instant = now.plus(1, ChronoUnit.HOURS),
        status: TimeDealStatus = TimeDealStatus.ACTIVE,
        maxPerUser: Int = 5,
    ) = TimeDeal(
        id = UUID.randomUUID(),
        productId = UUID.randomUUID(),
        dealPrice = BigDecimal("5000"),
        originalPrice = BigDecimal("10000"),
        totalStock = 100,
        remainingStock = 50,
        maxPerUser = maxPerUser,
        startAt = startAt,
        endAt = endAt,
        status = status,
        createdAt = now,
        version = 0,
    )

    @Test
    fun `활성 딜에 유효 수량 → OK`() {
        val result = validator.check(activeDeal(), quantity = 1, now = now)
        assertEquals(TimeDealValidator.Check.OK, result)
    }

    @Test
    fun `아직 시작 전 → NOT_STARTED`() {
        val deal = activeDeal(
            startAt = now.plus(1, ChronoUnit.HOURS),
            endAt = now.plus(2, ChronoUnit.HOURS),
        )
        val result = validator.check(deal, quantity = 1, now = now)
        assertEquals(TimeDealValidator.Check.NOT_STARTED, result)
    }

    @Test
    fun `이미 종료 → ENDED`() {
        val deal = activeDeal(
            startAt = now.minus(2, ChronoUnit.HOURS),
            endAt = now.minus(1, ChronoUnit.HOURS),
        )
        val result = validator.check(deal, quantity = 1, now = now)
        assertEquals(TimeDealValidator.Check.ENDED, result)
    }

    @Test
    fun `상태가 SCHEDULED → INACTIVE`() {
        val deal = activeDeal(status = TimeDealStatus.SCHEDULED)
        val result = validator.check(deal, quantity = 1, now = now)
        assertEquals(TimeDealValidator.Check.INACTIVE, result)
    }

    @Test
    fun `수량 0 → QUANTITY_INVALID`() {
        val result = validator.check(activeDeal(), quantity = 0, now = now)
        assertEquals(TimeDealValidator.Check.QUANTITY_INVALID, result)
    }

    @Test
    fun `수량이 maxPerUser 초과 → QUANTITY_INVALID`() {
        val deal = activeDeal(maxPerUser = 1)
        val result = validator.check(deal, quantity = 2, now = now)
        assertEquals(TimeDealValidator.Check.QUANTITY_INVALID, result)
    }
}
