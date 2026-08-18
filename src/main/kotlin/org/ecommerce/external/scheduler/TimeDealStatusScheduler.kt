package org.ecommerce.external.scheduler

import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class TimeDealStatusScheduler(
    private val timeDealCommandPort: TimeDealCommandPort,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TimeDealStatusScheduler::class.java)
    }

    @Transactional
    @Scheduled(fixedDelay = 5_000)
    fun syncStatuses() {
        val now = Instant.now()
        val activated = timeDealCommandPort.activateDueDeals(now)
        val ended = timeDealCommandPort.endExpiredDeals(now)

        if (activated > 0 || ended > 0) {
            log.info("TimeDeal status synced: activated=$activated, ended=$ended")
        }
    }
}
