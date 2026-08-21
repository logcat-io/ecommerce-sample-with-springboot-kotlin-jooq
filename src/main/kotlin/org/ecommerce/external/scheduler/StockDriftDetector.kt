package org.ecommerce.external.scheduler

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Redis 와 DB 의 재고가 어긋났는지 감시한다. 고치지는 않는다.
 *
 * 예전에는 차이를 발견하면 INCRBY 로 되돌렸는데, 그게 정상 동작을 이상으로 판정했다.
 * 구매는 Redis 를 먼저 줄이고 DB 는 커밋 뒤에 줄어들기 때문에, 진행 중인 구매는
 * 정의상 Redis < DB 다. 그걸 되돌리면 이미 팔린 재고가 살아나고, 그 구매가 커밋되면
 * 이번엔 Redis > DB 가 되어 자기가 만든 불일치를 다음 틱에 알람으로 띄웠다.
 *
 * 차이가 남았다는 건 보정할 일이 아니라 어딘가 끊겼다는 신호다 — 프로세스가 죽었거나,
 * Redis 와의 연결이 끊겼거나, DB 커밋이 실패했거나. 사람이 판단할 문제라 감지까지만 한다.
 */
@Component
class StockDriftDetector(
    private val timeDealQueryPort: TimeDealQueryPort,
    private val stockPort: StockPort,
    meterRegistry: MeterRegistry,
) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        /** 이 횟수만큼 연속으로 같은 방향의 차이가 보이면 진행 중 구매로 설명할 수 없다. */
        private const val PERSIST_THRESHOLD = 2
    }

    private val consecutiveDrift = ConcurrentHashMap<UUID, AtomicInteger>()
    private val driftingDeals = AtomicInteger(0)

    init {
        meterRegistry.gauge(
            "timedeal.stock.drifting.deals",
            Tags.empty(),
            driftingDeals,
        ) { it.get().toDouble() }
    }

    @Scheduled(fixedDelay = 60_000)
    fun detect() {
        val activeDeals = timeDealQueryPort.findAllActive()
        var drifting = 0

        for (deal in activeDeals) {
            // 스냅샷의 재고를 그대로 쓰면 조회 시점과 Redis 읽는 시점이 벌어진다.
            // 그 사이에 커밋된 구매만큼 차이가 부풀어 없는 이상을 만든다.
            val redisStock = stockPort.getRemaining(deal.id)
            val dbStock = timeDealQueryPort.findById(deal.id)?.remainingStock ?: continue

            if (redisStock == dbStock) {
                consecutiveDrift.remove(deal.id)
                continue
            }

            drifting++
            val streak = consecutiveDrift
                .computeIfAbsent(deal.id) { AtomicInteger(0) }
                .incrementAndGet()

            when {
                // 구매는 Redis 를 먼저 줄인다. 이 방향은 설명할 경로가 없다.
                redisStock > dbStock -> log.error(
                    "[StockDrift] deal={} Redis({}) > DB({}) - 설명 불가. 즉시 확인 필요",
                    deal.id, redisStock, dbStock,
                )

                // 진행 중 구매로 설명되는 방향이지만, 계속 남으면 얘기가 다르다.
                streak >= PERSIST_THRESHOLD -> log.error(
                    "[StockDrift] deal={} Redis({}) < DB({}) 가 {}회 연속. 진행 중 구매로 설명되지 않는다 - 장애 의심",
                    deal.id, redisStock, dbStock, streak,
                )

                else -> log.warn(
                    "[StockDrift] deal={} Redis({}) < DB({}) - 진행 중 구매일 수 있어 관찰한다",
                    deal.id, redisStock, dbStock,
                )
            }
        }

        driftingDeals.set(drifting)
        consecutiveDrift.keys.retainAll(activeDeals.map { it.id }.toSet())
    }
}
