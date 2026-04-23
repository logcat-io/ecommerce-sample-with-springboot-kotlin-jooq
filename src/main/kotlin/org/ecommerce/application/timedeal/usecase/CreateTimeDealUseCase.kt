package org.ecommerce.application.timedeal.usecase

import org.ecommerce.application.timedeal.dto.CreateTimeDealCommand
import org.ecommerce.application.timedeal.dto.CreateTimeDealResult
import org.ecommerce.core.timedeal.model.TimeDeal
import org.ecommerce.core.timedeal.port.StockPort
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateTimeDealUseCase(
    private val timeDealCommandPort: TimeDealCommandPort,
    private val stockPort: StockPort,
    ) {

    @Transactional
    fun execute(
        command: CreateTimeDealCommand
    ): CreateTimeDealResult {
        val deal = TimeDeal.create(
            productId = command.productId,
            dealPrice = command.dealPrice,
            originalPrice = command.originalPrice,
            totalStock = command.totalStock,
            maxPerUser = command.maxPerUser,
            startAt =  command.startAt,
            endAt = command.endAt,
        )

        val saved = timeDealCommandPort.save(deal)

        stockPort.initialize(saved.id, saved.totalStock)

        return CreateTimeDealResult(
            id = saved.id,
            productId = saved.productId,
            dealPrice = saved.dealPrice,
            totalStock = saved.totalStock,
            status = saved.status.name,
            startAt = saved.startAt,
            endAt = saved.endAt,
        )
    }
}
