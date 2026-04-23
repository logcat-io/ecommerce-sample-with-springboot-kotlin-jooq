package org.ecommerce.external.persistence.jooq.timedeal

import com.fasterxml.uuid.Generators
import org.ecommerce.core.timedeal.model.TimeDeal
import org.ecommerce.core.timedeal.model.TimeDealStatus
import org.ecommerce.core.timedeal.port.TimeDealCommandPort
import org.ecommerce.core.timedeal.port.TimeDealQueryPort
import org.ecommerce.jooq.generated.tables.records.TimeDealPurchasesRecord
import org.ecommerce.jooq.generated.tables.records.TimeDealsRecord
import org.ecommerce.jooq.generated.tables.references.TIME_DEALS
import org.ecommerce.jooq.generated.tables.references.TIME_DEAL_PURCHASES
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetStep
import org.jooq.InsertSetStep
import org.springframework.stereotype.Repository
import java.time.ZoneOffset
import java.util.*

@Repository
class TimeDealJooqAdaptor(
    private val dsl: DSLContext
) : TimeDealCommandPort, TimeDealQueryPort {

    companion object {
        private val uuidGenerator = Generators.timeBasedEpochGenerator()
    }

    private fun insertFieldsForTimeDeal(
        deal: TimeDeal,
        q: InsertSetStep<TimeDealsRecord>
    ) = q
        .set(TIME_DEALS.ID, deal.id)
        .set(TIME_DEALS.PRODUCT_ID, deal.productId)
        .set(TIME_DEALS.DEAL_PRICE, deal.dealPrice)
        .set(TIME_DEALS.ORIGINAL_PRICE, deal.originalPrice)
        .set(TIME_DEALS.TOTAL_STOCK, deal.totalStock)
        .set(TIME_DEALS.REMAINING_STOCK, deal.remainingStock)
        .set(TIME_DEALS.MAX_PER_USER, deal.maxPerUser)
        .set(TIME_DEALS.START_AT, deal.startAt.atOffset(ZoneOffset.UTC))
        .set(TIME_DEALS.END_AT, deal.endAt.atOffset(ZoneOffset.UTC))
        .set(TIME_DEALS.STATUS, deal.status.name)
        .set(TIME_DEALS.CREATED_AT, deal.createdAt.atOffset(ZoneOffset.UTC))
        .set(TIME_DEALS.VERSION, deal.version)

    private fun updateFieldsForTimeDeal(
        deal: TimeDeal,
        q: InsertOnDuplicateSetStep<TimeDealsRecord>
    ) = q
        .set(TIME_DEALS.REMAINING_STOCK, deal.remainingStock)
        .set(TIME_DEALS.STATUS, deal.status.name)
        .set(TIME_DEALS.VERSION, deal.version)

    // save: upsert (insert ... on conflict do update)
    override fun save(deal: TimeDeal): TimeDeal {
        dsl.insertInto(TIME_DEALS)
            .let { insertFieldsForTimeDeal(deal, it) }
            .onConflict(TIME_DEALS.ID)
            .doUpdate()
            .let { updateFieldsForTimeDeal(deal, it) }
            .execute()

        return deal
    }

    // ── decreaseStockWithVersion: Optimistic Lock 핵심 쿼리 ──
    //
    // 이 메서드 하나가 DB 레벨 동시성 제어의 전부다.
    //
    // SQL:
    //   UPDATE time_deals
    //   SET remaining_stock = remaining_stock - :quantity,
    //       version = version + 1
    //   WHERE id = :id
    //     AND version = :expectedVersion
    //     AND remaining_stock >= :quantity
    //
    // 조건별 설명:
    //   id = :id                      → 대상 row 지정
    //   version = :expectedVersion    → Optimistic Lock. 내가 읽었을 때와 같은 version 이면 경합 없음.
    //   remaining_stock >= :quantity   → DB 레벨 음수 방지. Redis 가 먼저 통과시켰지만 DB 도 독립 검증.
    //
    // SET:
    //   remaining_stock = remaining_stock - :quantity  → 상대 차감 (절대값 SET 이 아님!)
    //   version = version + 1                          → 다음 요청의 CAS(Compare-And-Swap) 를 위해 증가
    //
    // 반환:
    //   execute() == 1  → 정확히 1개 row 업데이트 = 성공
    //   execute() == 0  → 조건 불일치 = 경합 패배 또는 재고 부족
    override fun decreaseStockWithVersion(
        id: UUID,
        quantity: Int,
        expectedVersion: Long
    ): Boolean {
        val updated = dsl.update(TIME_DEALS)
            .set(TIME_DEALS.REMAINING_STOCK, TIME_DEALS.REMAINING_STOCK.minus(quantity))
            .set(TIME_DEALS.VERSION, TIME_DEALS.VERSION.plus(1))
            .where(TIME_DEALS.ID.eq(id))
            .and(TIME_DEALS.VERSION.eq(expectedVersion))
            .and(TIME_DEALS.REMAINING_STOCK.ge(quantity))
            .execute()

        return updated == 1
    }

    private fun insertFieldsForTimeDealPurchases(
        timeDealId: UUID,
        userId: UUID,
        quantity: Int,
        q: InsertSetStep<TimeDealPurchasesRecord>
    ) = q
        .set(TIME_DEAL_PURCHASES.ID, uuidGenerator.generate())
        .set(TIME_DEAL_PURCHASES.TIME_DEAL_ID, timeDealId)
        .set(TIME_DEAL_PURCHASES.USER_ID, userId)
        .set(TIME_DEAL_PURCHASES.QUANTITY, quantity)

    private fun updateFieldsForTimeDealPurchases(
        quantity: Int,
        maxPerUser: Int,
        q: InsertOnDuplicateSetStep<TimeDealPurchasesRecord>
    ) = q
        .set(
            TIME_DEAL_PURCHASES.QUANTITY,
            TIME_DEAL_PURCHASES.QUANTITY.plus(quantity),
        )

    override fun savePurchaseRecord(
        timeDealId: UUID,
        userId: UUID,
        quantity: Int,
        maxPerUser: Int
    ): Boolean {
        val affected = dsl.insertInto(TIME_DEAL_PURCHASES)
            .let { insertFieldsForTimeDealPurchases(timeDealId, userId, quantity, it) }
            .onConflict(TIME_DEAL_PURCHASES.TIME_DEAL_ID, TIME_DEAL_PURCHASES.USER_ID)
            .doUpdate()
            .let { updateFieldsForTimeDealPurchases(quantity, maxPerUser, it) }
            .where(TIME_DEAL_PURCHASES.QUANTITY.plus(quantity).le(maxPerUser))
            .execute()

        return affected == 1
    }

    override fun findById(timeDealId: UUID): TimeDeal? =
        dsl.selectFrom(TIME_DEALS)
            .where(TIME_DEALS.ID.eq(timeDealId))
            .fetchOne { it.toDomain() }

    override fun getPurchasedQuantity(
        timeDealId: UUID,
        userId: UUID
    ): Int = dsl.select(TIME_DEAL_PURCHASES.QUANTITY)
        .from(TIME_DEAL_PURCHASES)
        .where(TIME_DEAL_PURCHASES.TIME_DEAL_ID.eq(timeDealId))
        .and(TIME_DEAL_PURCHASES.USER_ID.eq(userId))
        .fetchOne(TIME_DEAL_PURCHASES.QUANTITY) ?: 0

    override fun findCurrentVersion(timeDealId: UUID): Long? =
        dsl.select(TIME_DEALS.VERSION)
            .from(TIME_DEALS)
            .where(TIME_DEALS.ID.eq(timeDealId))
            .fetchOne(TIME_DEALS.VERSION)

    override fun findAllActive(): List<TimeDeal> =
        dsl.selectFrom(TIME_DEALS)
            .where(TIME_DEALS.STATUS.eq(TimeDealStatus.ACTIVE.name))
            .fetch { it.toDomain() }

    private fun TimeDealsRecord.toDomain(): TimeDeal = TimeDeal(
        id = this.id!!,
        productId = this.productId!!,
        dealPrice = this.dealPrice!!,
        originalPrice = this.originalPrice!!,
        totalStock = this.totalStock!!,
        remainingStock = this.remainingStock!!,
        maxPerUser = this.maxPerUser!!,
        startAt = this.startAt!!.toInstant(),
        endAt = this.endAt!!.toInstant(),
        status = TimeDealStatus.valueOf(this.status!!),
        createdAt = this.createdAt!!.toInstant(),
        version = this.version!!,
    )
}
