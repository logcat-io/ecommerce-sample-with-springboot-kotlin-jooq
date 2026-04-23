package org.ecommerce.core.integration.timedeal


import org.ecommerce.application.timedeal.usecase.CreateTimeDealUseCase
import org.ecommerce.application.timedeal.usecase.PurchaseTimeDealUseCase
import org.ecommerce.application.timedeal.dto.CreateTimeDealCommand
import org.ecommerce.application.timedeal.dto.PurchaseTimeDealCommand
import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
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
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

// ═══════════════════════════════════════════════════════════════
// 동시성 통합 테스트 — 이 프로젝트의 가장 중요한 테스트.
// ═══════════════════════════════════════════════════════════════
//
// 시나리오:
//   재고 100개 타임딜에 200명이 동시 구매 → 정확히 100명 성공, 100명 실패.
//   Redis 남은 재고 = 0, DB remaining_stock = 0.
//
// Testcontainers 로 실제 PostgreSQL + Redis 를 기동한다.
// Mock 이 아닌 실제 인프라에서 동시성을 검증하는 것이 핵심.
// ═══════════════════════════════════════════════════════════════
@SpringBootTest
@Testcontainers
class PurchaseTimeDealConcurrencyTest {

    companion object {
        // ── Testcontainers: 실제 PostgreSQL 16 ──
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("commerce_test")
            withUsername("test")
            withPassword("test")
        }

        // ── Testcontainers: 실제 Redis 7 ──
        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine").apply {
            withExposedPorts(6379)
        }

        // Spring Boot 가 Testcontainers 의 동적 포트를 사용하도록 설정.
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

    @Autowired lateinit var purchaseUseCase: PurchaseTimeDealUseCase
    @Autowired lateinit var createUseCase: CreateTimeDealUseCase
    @Autowired lateinit var stockPort: StockPort
    @Autowired lateinit var timeDealQueryPort: TimeDealQueryPort
    @Autowired lateinit var dsl: org.jooq.DSLContext

    // ── 헬퍼: FK를 만족하도록 products 테이블에 직접 INSERT ──
    // Phase 1의 CreateProductUseCase 없이 독립적으로 테스트 데이터를 생성한다.
    // jOOQ 메타모델을 사용하여 타입 안전하게 삽입한다.
    private fun insertTestProduct(productId: UUID) {
        dsl.insertInto(org.ecommerce.jooq.generated.tables.references.PRODUCTS)
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.ID, productId)
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.NAME, "Test Product")
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.PRICE, java.math.BigDecimal("10000"))
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.CATEGORY, "test")
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.STATUS, "ACTIVE")
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.CREATED_AT, java.time.OffsetDateTime.now())
            .set(org.ecommerce.jooq.generated.tables.references.PRODUCTS.UPDATED_AT, java.time.OffsetDateTime.now())
            .execute()
    }

    @Test
    fun `100개 재고에 200명 동시 구매 시 정확히 100명 성공`() {
        val totalStock = 100
        val concurrentUsers = 200
        val now = Instant.now()

        // ── Given: 상품 + 타임딜 생성 ──
        // time_deals.product_id FK를 만족하도록 products 테이블에 먼저 INSERT한다.
        val productId = UUID.randomUUID()
        insertTestProduct(productId)

        val dealResult = createUseCase.execute(
            CreateTimeDealCommand(
                productId = productId,
                dealPrice = BigDecimal("5000"),
                originalPrice = BigDecimal("10000"),
                totalStock = totalStock,
                maxPerUser = 1,
                startAt = now.minus(1, ChronoUnit.HOURS),
                endAt = now.plus(1, ChronoUnit.HOURS),
            )
        )

        // 딜 상태를 ACTIVE 로 변경 (실무에서는 스케줄러가 담당).
        // 테스트에서는 jOOQ 로 DB 를 직접 UPDATE 한다.
        dsl.update(org.ecommerce.jooq.generated.tables.references.TIME_DEALS)
            .set(org.ecommerce.jooq.generated.tables.references.TIME_DEALS.STATUS, "ACTIVE")
            .where(org.ecommerce.jooq.generated.tables.references.TIME_DEALS.ID.eq(dealResult.id))
            .execute()

        val dealId = dealResult.id

        // ── When: 200명이 동시 구매 ──
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // CountDownLatch 패턴:
        //   readyLatch: 모든 스레드가 준비될 때까지 대기.
        //   doneLatch: 모든 스레드가 완료될 때까지 메인 스레드 대기.
        val readyLatch = CountDownLatch(concurrentUsers)
        val startLatch = CountDownLatch(1)          // 모든 스레드가 동시에 시작
        val doneLatch = CountDownLatch(concurrentUsers)

        val executor = Executors.newFixedThreadPool(concurrentUsers)

        repeat(concurrentUsers) { i ->
            executor.submit {
                try {
                    readyLatch.countDown()          // "나 준비됐어"
                    startLatch.await()               // "출발 신호 대기"

                    purchaseUseCase.execute(
                        PurchaseTimeDealCommand(
                            timeDealId = dealId,
                            userId = UUID.randomUUID(),  // 매번 다른 사용자
                            quantity = 1,
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()      // 200개 스레드 모두 준비 완료
        startLatch.countDown()   // 일제히 출발!
        doneLatch.await()        // 모두 완료 대기

        executor.shutdown()

        // ── Then: 정확히 100 성공, 100 실패 ──
        assertEquals(totalStock, successCount.get(),
            "성공 수가 재고(${totalStock})와 일치해야 한다")
        assertEquals(concurrentUsers - totalStock, failCount.get(),
            "실패 수가 초과 요청 수와 일치해야 한다")

        // ── Redis 와 DB 의 남은 재고가 모두 0 ──
        val redisRemaining = stockPort.getRemaining(dealId)
        assertEquals(0, redisRemaining, "Redis 남은 재고가 0 이어야 한다")

        val dbDeal = timeDealQueryPort.findById(dealId)!!
        assertEquals(0, dbDeal.remainingStock, "DB remaining_stock 이 0 이어야 한다")
    }
}
