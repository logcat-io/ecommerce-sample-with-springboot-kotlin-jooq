package org.ecommerce.core.timedeal.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TimeDeal(
    val id: UUID,
    val productId: UUID,
    val dealPrice: BigDecimal,
    val originalPrice: BigDecimal,
    val totalStock: Int,
    val remainingStock: Int,
    val maxPerUser: Int,
    val startAt: Instant,
    val endAt: Instant,
    val status: TimeDealStatus,
    val createdAt: Instant,
    val version: Long
){
    init {
        // ── 도메인 불변식 ──
        // 이 조건 중 하나라도 위반하면 IllegalArgumentException 이 즉시 발생한다.
        // 잘못된 상태의 TimeDeal 객체가 시스템에 존재할 수 없도록 보장.

        // 재고는 음수가 될 수 없다.
        require(totalStock >= 0) { "totalStock must be >= 0" }

        // 남은 재고는 0 ~ 전체 재고 사이.
        require(remainingStock in 0..totalStock) { "remainingStock out of range" }

        // 딜 가격은 원래 가격 이하여야 한다 (할인이니까).
        require(dealPrice <= originalPrice) { "dealPrice must be <= originalPrice" }

        // 종료 시각이 시작 시각보다 뒤여야 한다.
        require(endAt.isAfter(startAt)) { "endAt must be after startAt" }

        // 1인당 최소 1개는 구매할 수 있어야 한다.
        require(maxPerUser >= 1) { "maxPerUser must be >= 1" }
    }

    fun isActiveAt(now: Instant): Boolean =
        now in startAt..endAt && status == TimeDealStatus.ACTIVE

    companion object {
        fun create(
            productId: UUID,
            dealPrice: BigDecimal,
            originalPrice: BigDecimal,
            totalStock: Int,
            maxPerUser: Int,
            startAt: Instant,
            endAt: Instant,
            now: Instant = Instant.now(),
        ): TimeDeal = TimeDeal(
            id = UUID.randomUUID(),
            productId = productId,
            dealPrice = dealPrice,
            originalPrice = originalPrice,
            totalStock = totalStock,
            remainingStock = totalStock,
            maxPerUser = maxPerUser,
            startAt = startAt,
            endAt = endAt,
            status = TimeDealStatus.SCHEDULED,
            createdAt = now,
            version = 0,
        )
    }
}
