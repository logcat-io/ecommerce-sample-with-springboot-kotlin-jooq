-- ============================================================================
-- V2: 타임딜 테이블 + 구매 이력 테이블
-- ============================================================================

-- time_deals: 타임딜 본체.
-- 이 테이블은 "확정된 재고 상태" 를 관리한다.
-- Redis 는 실시간 캐시이고, 이 테이블이 진실의 원천(Source of Truth) 이다.
CREATE TABLE time_deals
(
    -- PK: UUID v4. 외부 노출에 안전하고 분산 환경에서 충돌 없음.
    id              UUID PRIMARY KEY        DEFAULT uuid_generate_v7(),

    -- 어떤 상품의 타임딜인지. products 테이블 FK.
    product_id      UUID           NOT NULL REFERENCES products (id),

    -- 타임딜 특가. 원본 가격보다 반드시 같거나 낮아야 함 (도메인 불변식).
    deal_price      NUMERIC(12, 2) NOT NULL,

    -- 원래 상품 가격. 할인율 표시에 사용.
    original_price  NUMERIC(12, 2) NOT NULL,

    -- 전체 재고 수량. 타임딜 생성 후 변경 불가 (불변).
    total_stock     INT            NOT NULL,

    -- 남은 재고 수량.
    -- Optimistic Lock UPDATE 의 대상 컬럼.
    -- WHERE remaining_stock >= ? 로 음수 방지.
    remaining_stock INT            NOT NULL,

    -- 1인당 최대 구매 수량. 기본값 1 (1인 1개 제한).
    max_per_user    INT            NOT NULL DEFAULT 1,

    -- 타임딜 시작/종료 시각. TIMESTAMPTZ 로 타임존 안전.
    start_at        TIMESTAMPTZ    NOT NULL,
    end_at          TIMESTAMPTZ    NOT NULL,

    -- 상태: SCHEDULED → ACTIVE → (SOLD_OUT | ENDED)
    -- 문자열로 저장 — DB enum 타입은 ALTER TYPE 비용이 커서 의도적 회피.
    status          VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED',

    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    -- =========================================================================
    -- version: Optimistic Lock 의 핵심.
    -- =========================================================================
    -- 왜 version 이 필요한가?
    --
    -- 동시에 2개의 요청이 같은 row 를 읽었다고 하자:
    --   요청 A: remaining_stock=5, version=3 을 읽음
    --   요청 B: remaining_stock=5, version=3 을 읽음
    --
    -- Optimistic Lock 없이 UPDATE 하면:
    --   A: SET remaining_stock = 4  → 성공
    --   B: SET remaining_stock = 4  → 성공 (!)  ← Oversell 발생
    --
    -- Optimistic Lock 으로:
    --   A: UPDATE ... SET remaining_stock = remaining_stock - 1, version = 4
    --      WHERE version = 3  → 성공 (affected=1)
    --   B: UPDATE ... SET remaining_stock = remaining_stock - 1, version = 4
    --      WHERE version = 3  → 실패 (affected=0, version 이 이미 4)
    --
    -- B 는 실패를 감지하고 Redis 재고를 롤백한다.
    -- =========================================================================
    version         BIGINT         NOT NULL DEFAULT 0
);

-- 상태별 조회 (ACTIVE 딜 목록).
CREATE INDEX idx_timedeals_status ON time_deals (status);
-- 시작 시각 기준 스케줄러 조회.
CREATE INDEX idx_timedeals_start_at ON time_deals (start_at);

-- ============================================================================
-- time_deal_purchases: 사용자별 누적 구매 수량 관리.
-- ============================================================================
-- 왜 별도 테이블인가?
--   1. 구매 이력은 타임딜 본체와 생명주기가 다르다 (딜 종료 후에도 이력은 남음).
--   2. UNIQUE(time_deal_id, user_id) + UPSERT 로 사용자별 단일 row 를 유지하면서
--      quantity 를 누적한다.
--
-- 흐름:
--   1) getPurchasedQuantity() 로 선검사 → 대부분 여기서 한도 초과 차단
--   2) savePurchaseRecord() 로 UPSERT:
--        INSERT → 첫 구매: 새 row 삽입
--        ON CONFLICT → 재구매: quantity += 요청수량 (WHERE quantity + ? <= maxPerUser)
--      WHERE 조건이 false 이면 UPDATE 가 실행되지 않아 affected=0 → 한도 초과 감지
--
-- 왜 2단계인가?
--   선검사(SELECT)와 UPSERT 사이에 다른 요청이 끼어들 수 있다.
--   WHERE 조건부 UPSERT 가 단일 SQL 로 check-and-increment 를 원자적으로 수행한다.
CREATE TABLE time_deal_purchases
(
    id           UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    time_deal_id UUID        NOT NULL REFERENCES time_deals (id),
    user_id      UUID        NOT NULL,
    quantity     INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 사용자별 단일 row 를 보장한다.
    -- UPSERT 의 ON CONFLICT 기준 키이자, 누적 quantity 의 anchor.
    UNIQUE (time_deal_id, user_id)
);
