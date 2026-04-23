package org.ecommerce.external.scheduler

import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StockReconciler(
    private val timeDealQueryPort: TimeDealQueryPort,
    private val stockPort: StockPort
) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(fixedDelay = 60_000)
    fun reconcile() {
        val activeDeals = timeDealQueryPort.findAllActive()

        for (deal in activeDeals) {
            val redisStock = stockPort.getRemaining(deal.id)
            val dbStock = deal.remainingStock

            when {
                redisStock < dbStock -> {
                    val diff = dbStock - redisStock
                    stockPort.increase(deal.id, diff)

                    log.warn(
                        "[Reconciler] deal=$deal.id Redis($redisStock) < DB($dbStock) - 비정상 상태. 운영자 확인 필요"
                    )
                }

                redisStock > dbStock -> {
                    log.error(
                        "[Reconciler] deal=$deal.id Redis($redisStock) > DB($dbStock) - 비정상 상태. 운영자 확인 필요"
                    )
                }

                else -> Unit
            }
        }
    }
}
