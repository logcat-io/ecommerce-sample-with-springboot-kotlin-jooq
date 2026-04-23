package org.ecommerce.core.timedeal.service


import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.assertIs

// StockDecreaser 의 핵심 시나리오를 Mock 으로 검증.
// Redis/DB 없이 로직만 테스트 — 빠르고 결정적.
@DisplayName("StockDecreaser: Redis+DB 2단계 재고 차감")
class StockDecreaserTest {

    private lateinit var stockPort: StockPort
    private lateinit var commandPort: TimeDealCommandPort
    private lateinit var queryPort: TimeDealQueryPort
    private lateinit var rollbackHandler: StockRollbackHandler
    private lateinit var sut: StockDecreaser

    private val dealId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        stockPort = mock()
        commandPort = mock()
        queryPort = mock()
        rollbackHandler = StockRollbackHandler(stockPort)
        sut = StockDecreaser(stockPort, commandPort, queryPort, rollbackHandler)
    }

    // ═══════════════════════════════════════════
    // 정책 1: 정상 흐름 — Redis + DB 모두 성공
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: Redis 와 DB 가 모두 성공하면 Success 를 반환해야 한다")
    inner class SuccessPolicy {

        @Test
        @DisplayName("Redis 차감 성공 + DB version 일치이면 Success 를 반환한다")
        fun decrease_redisAndDbSuccess_returnsSuccess() {
            whenever(stockPort.tryDecrease(dealId, 1)).thenReturn(true)
            whenever(queryPort.findCurrentVersion(dealId)).thenReturn(0L)
            whenever(commandPort.decreaseStockWithVersion(dealId, 1, 0L)).thenReturn(true)

            val result = sut.decrease(dealId, 1)

            assertIs<StockDecreaser.Result.Success>(result)
            verify(stockPort, never()).increase(any(), any())
        }
    }

    // ═══════════════════════════════════════════
    // 정책 2: Redis 거부 — DB 접근 없이 즉시 반환
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: Redis 재고 부족이면 DB 에 접근하지 않고 StockExhausted 를 반환해야 한다")
    inner class RedisExhaustedPolicy {

        @Test
        @DisplayName("Redis 차감 실패이면 DB 를 호출하지 않고 StockExhausted 를 반환한다")
        fun decrease_redisFails_returnsStockExhaustedWithoutDbCall() {
            whenever(stockPort.tryDecrease(dealId, 1)).thenReturn(false)

            val result = sut.decrease(dealId, 1)

            assertIs<StockDecreaser.Result.StockExhausted>(result)
            verify(commandPort, never()).decreaseStockWithVersion(any(), any(), any())
        }
    }

    // ═══════════════════════════════════════════
    // 정책 3: OL 재시도 — version 불일치 시 재조회 후 재시도
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: DB version 불일치 시 version 을 재조회하여 최대 3회 재시도해야 한다")
    inner class RetryPolicy {

        @Test
        @DisplayName("첫 시도 version 불일치 후 두 번째 시도에 성공하면 Success 를 반환한다")
        fun decrease_firstVersionConflictThenSuccess_returnsSuccess() {
            whenever(stockPort.tryDecrease(dealId, 1)).thenReturn(true)
            // 1회: version=0 불일치, 2회: version=1 일치
            whenever(queryPort.findCurrentVersion(dealId)).thenReturn(0L, 1L)
            whenever(commandPort.decreaseStockWithVersion(dealId, 1, 0L)).thenReturn(false)
            whenever(commandPort.decreaseStockWithVersion(dealId, 1, 1L)).thenReturn(true)

            val result = sut.decrease(dealId, 1)

            assertIs<StockDecreaser.Result.Success>(result)
            verify(stockPort, never()).increase(any(), any())
        }

        @Test
        @DisplayName("MAX_RETRY(3회) 모두 version 불일치이면 Redis 를 복구하고 VersionConflict 를 반환한다")
        fun decrease_allRetriesVersionConflict_rollbacksRedisAndReturnsVersionConflict() {
            whenever(stockPort.tryDecrease(dealId, 1)).thenReturn(true)
            whenever(queryPort.findCurrentVersion(dealId)).thenReturn(0L)
            whenever(commandPort.decreaseStockWithVersion(any(), any(), any())).thenReturn(false)

            val result = sut.decrease(dealId, 1)

            assertIs<StockDecreaser.Result.VersionConflict>(result)
            verify(stockPort).increase(dealId, 1)
        }
    }
}
