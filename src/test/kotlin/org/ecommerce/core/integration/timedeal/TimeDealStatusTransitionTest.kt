package org.ecommerce.core.integration.timedeal

import org.ecommerce.core.timedeal.model.TimeDeal
import org.ecommerce.core.timedeal.model.TimeDealStatus
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.ecommerce.jooq.generated.tables.references.PRODUCTS
import org.ecommerce.jooq.generated.tables.references.TIME_DEALS
import org.ecommerce.jooq.generated.tables.references.TIME_DEAL_PURCHASES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

// 상태 전이 SQL 의 경계 조건을 검증한다.
// le / gt / in 을 하나만 잘못 써도 딜이 안 열리거나 끝난 딜이 되살아나는데,
// 단위 테스트로는 잡히지 않는다.
@SpringBootTest
@Testcontainers
@DisplayName("TimeDealCommandPort: 상태 전이")
class TimeDealStatusTransitionTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("commerce_test")
            withUsername("test")
            withPassword("test")
        }

        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine").apply {
            withExposedPorts(6379)
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }

    @Autowired lateinit var sut: TimeDealCommandPort
    @Autowired lateinit var queryPort: TimeDealQueryPort
    @Autowired lateinit var dsl: DSLContext

    private lateinit var productId: UUID
    private val now: Instant = Instant.parse("2026-08-18T10:00:00Z")

    @BeforeEach
    fun setUp() {
        // 전이 쿼리는 테이블 전체를 대상으로 하므로, 앞 테스트가 남긴 행이 있으면
        // 갱신 건수 단언이 오염된다.
        dsl.deleteFrom(TIME_DEAL_PURCHASES).where(DSL.trueCondition()).execute()
        dsl.deleteFrom(TIME_DEALS).where(DSL.trueCondition()).execute()
        dsl.deleteFrom(PRODUCTS).where(DSL.trueCondition()).execute()

        productId = UUID.randomUUID()
        dsl.insertInto(PRODUCTS)
            .set(PRODUCTS.ID, productId)
            .set(PRODUCTS.NAME, "전이 테스트용")
            .set(PRODUCTS.PRICE, BigDecimal("10000"))
            .set(PRODUCTS.CATEGORY, "test")
            .set(PRODUCTS.STATUS, "ACTIVE")
            .set(PRODUCTS.CREATED_AT, now.atOffset(ZoneOffset.UTC))
            .set(PRODUCTS.UPDATED_AT, now.atOffset(ZoneOffset.UTC))
            .execute()
    }

    private fun saveDeal(
        status: TimeDealStatus,
        startAt: Instant,
        endAt: Instant,
    ): UUID {
        val deal = TimeDeal(
            id = UUID.randomUUID(),
            productId = productId,
            dealPrice = BigDecimal("5000"),
            originalPrice = BigDecimal("10000"),
            totalStock = 100,
            remainingStock = 100,
            maxPerUser = 1,
            startAt = startAt,
            endAt = endAt,
            status = status,
            createdAt = startAt.minus(1, ChronoUnit.HOURS),
            version = 0,
        )
        sut.save(deal)
        return deal.id
    }

    private fun statusOf(id: UUID) = queryPort.findById(id)!!.status

    // ═══════════════════════════════════════════
    // 정책 1: 활성화 — 시작 시각이 지난 SCHEDULED 만 ACTIVE 가 된다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 판매 구간에 들어온 SCHEDULED 딜만 ACTIVE 로 전이되어야 한다")
    inner class ActivationPolicy {

        @Test
        @DisplayName("시작 1분 전 SCHEDULED 딜은 전이되지 않는다")
        fun activateDueDeals_beforeStart_notTransitioned() {
            val id = saveDeal(
                TimeDealStatus.SCHEDULED,
                startAt = now.plus(1, ChronoUnit.MINUTES),
                endAt = now.plus(1, ChronoUnit.HOURS),
            )

            assertEquals(0, sut.activateDueDeals(now))
            assertEquals(TimeDealStatus.SCHEDULED, statusOf(id))
        }

        @Test
        @DisplayName("시작 시각 정각이면 전이된다 — 경계 포함")
        fun activateDueDeals_exactlyAtStart_transitioned() {
            val id = saveDeal(
                TimeDealStatus.SCHEDULED,
                startAt = now,
                endAt = now.plus(1, ChronoUnit.HOURS),
            )

            assertEquals(1, sut.activateDueDeals(now))
            assertEquals(TimeDealStatus.ACTIVE, statusOf(id))
        }

        @Test
        @DisplayName("이미 종료 시각이 지난 SCHEDULED 딜은 ACTIVE 로 되살아나지 않는다")
        fun activateDueDeals_alreadyExpired_notTransitioned() {
            val id = saveDeal(
                TimeDealStatus.SCHEDULED,
                startAt = now.minus(2, ChronoUnit.HOURS),
                endAt = now.minus(1, ChronoUnit.HOURS),
            )

            assertEquals(0, sut.activateDueDeals(now))
            assertEquals(TimeDealStatus.SCHEDULED, statusOf(id))
        }

        @Test
        @DisplayName("이미 ACTIVE 인 딜은 다시 세지 않는다 — 워커가 매번 같은 행을 갱신하지 않는다")
        fun activateDueDeals_alreadyActive_notCountedAgain() {
            saveDeal(
                TimeDealStatus.ACTIVE,
                startAt = now.minus(1, ChronoUnit.HOURS),
                endAt = now.plus(1, ChronoUnit.HOURS),
            )

            assertEquals(0, sut.activateDueDeals(now))
        }
    }

    // ═══════════════════════════════════════════
    // 정책 2: 종료 — 종료 시각이 지나면 상태와 무관하게 ENDED 가 된다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 종료 시각이 지난 딜은 SCHEDULED·ACTIVE 모두 ENDED 로 전이되어야 한다")
    inner class ExpirationPolicy {

        @Test
        @DisplayName("종료 시각 정각이면 ACTIVE 딜이 ENDED 로 전이된다 — 경계 포함")
        fun endExpiredDeals_exactlyAtEnd_transitioned() {
            val id = saveDeal(
                TimeDealStatus.ACTIVE,
                startAt = now.minus(1, ChronoUnit.HOURS),
                endAt = now,
            )

            assertEquals(1, sut.endExpiredDeals(now))
            assertEquals(TimeDealStatus.ENDED, statusOf(id))
        }

        @Test
        @DisplayName("한 번도 열리지 않은 SCHEDULED 딜도 종료 시각이 지나면 ENDED 가 된다")
        fun endExpiredDeals_neverOpenedScheduled_transitioned() {
            val id = saveDeal(
                TimeDealStatus.SCHEDULED,
                startAt = now.minus(2, ChronoUnit.HOURS),
                endAt = now.minus(1, ChronoUnit.HOURS),
            )

            assertEquals(1, sut.endExpiredDeals(now))
            assertEquals(TimeDealStatus.ENDED, statusOf(id))
        }

        @Test
        @DisplayName("아직 진행 중인 딜은 전이되지 않는다")
        fun endExpiredDeals_stillRunning_notTransitioned() {
            val id = saveDeal(
                TimeDealStatus.ACTIVE,
                startAt = now.minus(1, ChronoUnit.HOURS),
                endAt = now.plus(1, ChronoUnit.MINUTES),
            )

            assertEquals(0, sut.endExpiredDeals(now))
            assertEquals(TimeDealStatus.ACTIVE, statusOf(id))
        }
    }
}
