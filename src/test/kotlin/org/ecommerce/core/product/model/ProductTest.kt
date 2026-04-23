package org.ecommerce.core.product.model

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("Product: 상품 도메인 모델")
class ProductTest {

    private lateinit var sut: Product

    @BeforeEach
    fun setUp() {
        sut = Product.create(
            name = "아메리카노",
            description = "에스프레소 + 물",
            price = BigDecimal("4500"),
            category = "beverage",
        )
    }

    // ═══════════════════════════════════════════
    // 정책 1: 생성 불변식 — 잘못된 입력은 생성 자체를 막는다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 유효하지 않은 입력으로 생성 시 예외가 발생해야 한다")
    inner class CreateValidationPolicy {

        @Test
        @DisplayName("name이 빈 문자열이면 IllegalArgumentException이 발생한다")
        fun create_blankName_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                Product.create(name = "", description = null, price = BigDecimal("1000"), category = "food")
            }
        }

        @Test
        @DisplayName("name이 공백만 있으면 IllegalArgumentException이 발생한다")
        fun create_whitespaceOnlyName_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                Product.create(name = "   ", description = null, price = BigDecimal("1000"), category = "food")
            }
        }

        @Test
        @DisplayName("category가 빈 문자열이면 IllegalArgumentException이 발생한다")
        fun create_blankCategory_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                Product.create(name = "상품", description = null, price = BigDecimal("1000"), category = "")
            }
        }

        @Test
        @DisplayName("price가 0이면 IllegalArgumentException이 발생한다")
        fun create_zeroPrice_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                Product.create(name = "상품", description = null, price = BigDecimal.ZERO, category = "food")
            }
        }

        @Test
        @DisplayName("price가 음수이면 IllegalArgumentException이 발생한다")
        fun create_negativePrice_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                Product.create(name = "상품", description = null, price = BigDecimal("-1"), category = "food")
            }
        }
    }

    // ═══════════════════════════════════════════
    // 정책 2: 생성 초기 상태 — 신규 상품은 INACTIVE로 시작한다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 생성된 상품은 INACTIVE 상태로 초기화되어야 한다")
    inner class CreateInitialStatePolicy {

        @Test
        @DisplayName("유효한 입력으로 생성하면 status는 INACTIVE이다")
        fun create_validInputs_statusIsInactive() {
            assertEquals(ProductStatus.INACTIVE, sut.status)
        }

        @Test
        @DisplayName("description이 null이면 null로 저장된다")
        fun create_nullDescription_descriptionIsNull() {
            val product = Product.create(
                name = "설명없는상품",
                description = null,
                price = BigDecimal("1000"),
                category = "food",
            )
            assertNull(product.description)
        }

        @Test
        @DisplayName("createdAt과 updatedAt은 동일한 now 값으로 초기화된다")
        fun create_validInputs_createdAtEqualsUpdatedAt() {
            val now = Instant.parse("2026-04-23T00:00:00Z")
            val product = Product.create(
                name = "타임스탬프 상품",
                description = null,
                price = BigDecimal("1000"),
                category = "food",
                now = now,
            )
            assertEquals(now, product.createdAt)
            assertEquals(now, product.updatedAt)
        }
    }

    // ═══════════════════════════════════════════
    // 정책 3: 비활성화 — deactivate 호출 시 상태와 시각이 갱신된다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 비활성화 시 status가 INACTIVE로 변경되고 updatedAt이 갱신되어야 한다")
    inner class DeactivatePolicy {

        @Test
        @DisplayName("deactivate 호출 시 status는 INACTIVE이고 updatedAt은 전달된 시각이다")
        fun deactivate_activeProduct_statusIsInactiveAndUpdatedAtIsNow() {
            val now = Instant.parse("2026-04-23T12:00:00Z")

            val deactivated = sut.deactivate(now)

            assertEquals(ProductStatus.INACTIVE, deactivated.status)
            assertEquals(now, deactivated.updatedAt)
        }

        @Test
        @DisplayName("deactivate 호출 시 id, name, price 등 나머지 필드는 변경되지 않는다")
        fun deactivate_activeProduct_otherFieldsUnchanged() {
            val now = Instant.parse("2026-04-23T12:00:00Z")

            val deactivated = sut.deactivate(now)

            assertEquals(sut.id, deactivated.id)
            assertEquals(sut.name, deactivated.name)
            assertEquals(sut.price, deactivated.price)
            assertEquals(sut.category, deactivated.category)
        }
    }

    // ═══════════════════════════════════════════
    // 정책 4: 정보 수정 — 변경된 필드만 반영되고 status는 ACTIVE로 복원된다
    // ═══════════════════════════════════════════

    @Nested
    @DisplayName("정책: 상품 정보 수정 시 변경된 필드가 반영되고 status가 ACTIVE로 복원되어야 한다")
    inner class UpdateDetailsPolicy {

        @Test
        @DisplayName("유효한 정보로 수정하면 name, price, category가 변경되고 status는 ACTIVE이다")
        fun updateDetails_validInputs_fieldsUpdatedAndStatusIsActive() {
            val now = Instant.parse("2026-04-23T12:00:00Z")

            val updated = sut.updateDetails(
                name = "콜드브루",
                description = "차갑게 추출",
                price = BigDecimal("5500"),
                category = "beverage",
                now = now,
            )

            assertEquals("콜드브루", updated.name)
            assertEquals("차갑게 추출", updated.description)
            assertEquals(BigDecimal("5500"), updated.price)
            assertEquals(ProductStatus.ACTIVE, updated.status)
            assertEquals(now, updated.updatedAt)
        }

        @Test
        @DisplayName("id와 createdAt은 수정 후에도 변경되지 않는다")
        fun updateDetails_validInputs_idAndCreatedAtUnchanged() {
            val updated = sut.updateDetails(
                name = "콜드브루",
                description = null,
                price = BigDecimal("5500"),
                category = "beverage",
            )

            assertEquals(sut.id, updated.id)
            assertEquals(sut.createdAt, updated.createdAt)
        }

        @Test
        @DisplayName("name을 공백으로 수정하면 IllegalArgumentException이 발생한다")
        fun updateDetails_blankName_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                sut.updateDetails(name = "", description = null, price = BigDecimal("1000"), category = "food")
            }
        }

        @Test
        @DisplayName("price를 0으로 수정하면 IllegalArgumentException이 발생한다")
        fun updateDetails_zeroPrice_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                sut.updateDetails(name = "상품", description = null, price = BigDecimal.ZERO, category = "food")
            }
        }

        @Test
        @DisplayName("price를 음수로 수정하면 IllegalArgumentException이 발생한다")
        fun updateDetails_negativePrice_throwsIllegalArgumentException() {
            assertFailsWith<IllegalArgumentException> {
                sut.updateDetails(name = "상품", description = null, price = BigDecimal("-500"), category = "food")
            }
        }
    }
}
