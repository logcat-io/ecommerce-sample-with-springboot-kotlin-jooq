package org.ecommerce.external.scheduler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.ecommerce.core.timedeal.model.TimeDeal
import org.ecommerce.core.timedeal.model.TimeDealStatus
import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("StockDriftDetector: 재고 불일치 감지")
class StockDriftDetectorTest {

    private lateinit var sut: StockDriftDetector
    private lateinit var timeDealQueryPort: TimeDealQueryPort
    private lateinit var stockPort: StockPort
    private lateinit var registry: SimpleMeterRegistry

    private val dealId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        timeDealQueryPort = mock()
        stockPort = mock()
        registry = SimpleMeterRegistry()
        sut = StockDriftDetector(timeDealQueryPort, stockPort, registry)
    }

    private fun deal(remainingStock: Int) = TimeDeal(
        id = dealId,
        productId = UUID.randomUUID(),
        dealPrice = BigDecimal("1000"),
        originalPrice = BigDecimal("2000"),
        totalStock = 100,
        remainingStock = remainingStock,
        maxPerUser = 1,
        startAt = Instant.now().minusSeconds(60),
        endAt = Instant.now().plusSeconds(600),
        status = TimeDealStatus.ACTIVE,
        createdAt = Instant.now().minusSeconds(120),
        version = 1L,
    )

    private fun given(redis: Int, db: Int) {
        whenever(timeDealQueryPort.findAllActive()).thenReturn(listOf(deal(db)))
        whenever(timeDealQueryPort.findById(dealId)).thenReturn(deal(db))
        whenever(stockPort.getRemaining(dealId)).thenReturn(redis)
    }

    private fun driftingGauge(): Double =
        registry.get("timedeal.stock.drifting.deals").gauge().value()

    // ═══════════════════════════════════════════
    // 정책 1: 감지만 한다 — 재고를 건드리지 않는다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 불일치를 발견해도 재고를 보정하지 않아야 한다")
    inner class DetectOnlyPolicy {

        @Test
        @DisplayName("Redis 가 DB 보다 적어도 increase 를 호출하지 않는다")
        fun detect_redisLowerThanDb_doesNotIncrease() {
            // 진행 중 구매는 정의상 이 상태다. 되돌리면 이미 팔린 재고가 살아난다.
            given(redis = 7, db = 10)

            sut.detect()

            verify(stockPort, never()).increase(any(), any())
        }

        @Test
        @DisplayName("Redis 가 DB 보다 많아도 재고를 건드리지 않는다")
        fun detect_redisHigherThanDb_doesNotTouchStock() {
            given(redis = 10, db = 7)

            sut.detect()

            verify(stockPort, never()).increase(any(), any())
        }
    }

    // ═══════════════════════════════════════════
    // 정책 2: 비교 시점을 맞춘다 — 스냅샷 값을 그대로 쓰지 않는다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: DB 재고를 비교 직전에 다시 읽어야 한다")
    inner class FreshReadPolicy {

        @Test
        @DisplayName("목록 조회의 스냅샷이 아니라 findById 로 다시 읽는다")
        fun detect_always_reReadsDbBeforeCompare() {
            // 목록은 10 을 들고 있지만 지금 DB 는 7 이다.
            whenever(timeDealQueryPort.findAllActive()).thenReturn(listOf(deal(10)))
            whenever(timeDealQueryPort.findById(dealId)).thenReturn(deal(7))
            whenever(stockPort.getRemaining(dealId)).thenReturn(7)

            sut.detect()

            // 다시 읽었다면 7 == 7 이라 불일치가 아니다.
            // 스냅샷(10)을 썼다면 3 만큼 어긋난 것으로 잘못 센다.
            verify(timeDealQueryPort).findById(dealId)
            assertEquals(0.0, driftingGauge(), "다시 읽으면 불일치가 아니다")
        }
    }

    // ═══════════════════════════════════════════
    // 정책 3: 게이지 — 어긋난 딜 수를 노출한다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 어긋난 딜 수를 메트릭으로 노출해야 한다")
    inner class DriftGaugePolicy {

        @Test
        @DisplayName("불일치가 있으면 게이지가 1 이 된다")
        fun detect_withDrift_gaugeReflectsCount() {
            given(redis = 7, db = 10)

            sut.detect()

            assertEquals(1.0, driftingGauge())
        }

        @Test
        @DisplayName("불일치가 해소되면 게이지가 0 으로 돌아온다")
        fun detect_afterDriftResolved_gaugeReturnsToZero() {
            given(redis = 7, db = 10)
            sut.detect()
            assertEquals(1.0, driftingGauge())

            given(redis = 10, db = 10)
            sut.detect()

            assertEquals(0.0, driftingGauge(), "해소되면 0 이어야 한다")
        }
    }
}
