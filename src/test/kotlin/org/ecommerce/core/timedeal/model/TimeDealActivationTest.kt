package org.ecommerce.core.timedeal.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("TimeDeal: 판매 가능 여부 판단")
class TimeDealActivationTest {

    private val open = Instant.parse("2026-08-18T10:00:00Z")
    private val close = Instant.parse("2026-08-18T11:00:00Z")

    private fun deal(status: TimeDealStatus) = TimeDeal(
        id = UUID.randomUUID(),
        productId = UUID.randomUUID(),
        dealPrice = BigDecimal("5000"),
        originalPrice = BigDecimal("10000"),
        totalStock = 100,
        remainingStock = 50,
        maxPerUser = 1,
        startAt = open,
        endAt = close,
        status = status,
        createdAt = open.minusSeconds(3600),
        version = 0,
    )

    // ═══════════════════════════════════════════
    // 정책 1: 전이 지연 — 워커가 늦어도 오픈 시각에 판매가 열린다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 상태가 SCHEDULED 로 남아 있어도 판매 구간이면 구매할 수 있어야 한다")
    inner class ActivationLagPolicy {

        @Test
        @DisplayName("SCHEDULED 이고 오픈 1초 후이면 판매 가능하다")
        fun isActiveAt_scheduledWithinWindow_returnsTrue() {
            assertTrue(deal(TimeDealStatus.SCHEDULED).isActiveAt(open.plusSeconds(1)))
        }

        @Test
        @DisplayName("ACTIVE 이고 판매 구간 한가운데이면 판매 가능하다")
        fun isActiveAt_activeWithinWindow_returnsTrue() {
            assertTrue(deal(TimeDealStatus.ACTIVE).isActiveAt(open.plusSeconds(1800)))
        }

        @Test
        @DisplayName("시작 시각과 종료 시각 정각은 판매 구간에 포함된다")
        fun isActiveAt_exactBoundaries_returnsTrue() {
            val scheduled = deal(TimeDealStatus.SCHEDULED)
            assertTrue(scheduled.isActiveAt(open))
            assertTrue(scheduled.isActiveAt(close))
        }
    }

    // ═══════════════════════════════════════════
    // 정책 2: 시간 밖 — 상태와 무관하게 막는다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 판매 구간 밖이면 상태가 ACTIVE 여도 구매할 수 없어야 한다")
    inner class OutsideWindowPolicy {

        @Test
        @DisplayName("오픈 1초 전이면 SCHEDULED 든 ACTIVE 든 판매 불가다")
        fun isActiveAt_beforeStart_returnsFalse() {
            val justBefore = open.minusSeconds(1)
            assertFalse(deal(TimeDealStatus.SCHEDULED).isActiveAt(justBefore))
            assertFalse(deal(TimeDealStatus.ACTIVE).isActiveAt(justBefore))
        }

        @Test
        @DisplayName("종료 1초 후이면 상태가 ACTIVE 로 남아 있어도 판매 불가다")
        fun isActiveAt_afterEnd_returnsFalse() {
            assertFalse(deal(TimeDealStatus.ACTIVE).isActiveAt(close.plusSeconds(1)))
        }
    }

    // ═══════════════════════════════════════════
    // 정책 3: 종료 상태 — 시간과 무관하게 되돌리지 않는다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: SOLD_OUT 과 ENDED 는 판매 구간 안이어도 구매할 수 없어야 한다")
    inner class TerminalStatusPolicy {

        @Test
        @DisplayName("SOLD_OUT 이면 판매 구간 한가운데여도 판매 불가다")
        fun isActiveAt_soldOutWithinWindow_returnsFalse() {
            assertFalse(deal(TimeDealStatus.SOLD_OUT).isActiveAt(open.plusSeconds(1800)))
        }

        @Test
        @DisplayName("ENDED 이면 판매 구간 한가운데여도 판매 불가다")
        fun isActiveAt_endedWithinWindow_returnsFalse() {
            assertFalse(deal(TimeDealStatus.ENDED).isActiveAt(open.plusSeconds(1800)))
        }
    }
}
