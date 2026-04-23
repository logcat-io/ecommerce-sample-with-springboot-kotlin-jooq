package org.ecommerce.application.timedeal.config

import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.ecommerce.core.timedeal.service.StockDecreaser
import org.ecommerce.core.timedeal.service.StockRollbackHandler
import org.ecommerce.core.timedeal.service.TimeDealValidator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimeDealBeanConfig {

    @Bean
    fun timeDealValidator(): TimeDealValidator = TimeDealValidator()

    @Bean
    fun stockRollbackHandler(stockPort: StockPort): StockRollbackHandler =
        StockRollbackHandler(stockPort)

    @Bean
    fun stockDecreaser(
        stockPort: StockPort,
        timeDealCommandPort: TimeDealCommandPort,
        timeDealQueryPort: TimeDealQueryPort,
        stockRollbackHandler: StockRollbackHandler,
    ): StockDecreaser =
        StockDecreaser(stockPort, timeDealCommandPort, timeDealQueryPort, stockRollbackHandler)

}
