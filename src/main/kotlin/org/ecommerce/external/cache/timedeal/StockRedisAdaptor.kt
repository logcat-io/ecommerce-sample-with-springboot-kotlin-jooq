package org.ecommerce.external.cache.timedeal

import org.ecommerce.core.timedeal.port.StockPort
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class StockRedisAdaptor(
    private val redisTemplate: RedisTemplate<String, String>,
) : StockPort {

    private val decreaseScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            -- 1행: 현재 재고를 읽는다.
            --      GET 결과가 nil (키 없음) 이면 '0' 으로 처리
            --      tonumber 로 숫자 반환 - Redis 값은 항상 문자열이므로.
            
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            
            -- 2행: 차감할 수량을 숫자로 변환.
            local quantity = tonumber(ARGV[1])
            
            -- 3행: 재고가 부족하면 -1 을 반환하고 즉시 종료.
            --      어떤 쓰기 연산도 수행하지 않는다. - 원자적 실패.
            
            if current < quantity then
                return -1
            end
            
            -- 4행: 재고가 충분하므로 DECRBY 로 차감.
            --      DECRBY 는 원자적 연산으로, 성공하면 차감된 재고를 반환한다.
            --      이 시점에서 current >= quantity 가 보장된다.
            return redis.call('DECRBY', KEYS[1], quantity)            
        """.trimIndent()
        )
        resultType = Long::class.java
    }

    override fun tryDecrease(timeDealId: UUID, quantity: Int): Boolean {
        val result = redisTemplate.execute(
            decreaseScript,
            listOf(key(timeDealId)),  // KEYS 배열 - 단일 키
            quantity.toString(), // ARGV 배열 - 차감 수량
        )

        // result == null -> Redis 장애.
        return result != null && result >= 0
    }

    override fun increase(timeDealId: UUID, quantity: Int) {
        // INCRBY 는 단일 명령으로 그 자체로 원자적
        // LUA 스크립트가 필요 없다 - 복구 시에는 "조건 확인"이 불필요.
        redisTemplate.opsForValue().increment(key(timeDealId), quantity.toLong())
    }

    override fun getRemaining(timeDealId: UUID): Int =
        redisTemplate.opsForValue().get(key(timeDealId))?.toInt() ?: 0

    override fun initialize(timeDealId: UUID, stock: Int) {
        // SET 으로 정확히 1회 세팅.
        // SETNX 를 고려할 수 있다.
        redisTemplate.opsForValue().set(key(timeDealId), stock.toString())
    }

    // ─── Redis 키 네이밍 ─────────────────────────────────────
    // 형식: stock:{timeDealId}
    //
    // Redis Cluster 고려:
    //   Lua 스크립트에서 사용하는 모든 키는 같은 슬롯에 있어야 한다.
    //   이 어댑터는 단일 키만 사용하므로 슬롯 문제가 없다.
    //   만약 여러 키를 함께 다뤄야 하면 Hash Tag {deal-id} 를 사용:
    //     예: "stock:{deal-id}", "limit:{deal-id}" → {deal-id} 가 슬롯 결정.
    // ─────────────────────────────────────────────────────────
    private fun key(timeDealId: UUID) = "stock:$timeDealId"
}
