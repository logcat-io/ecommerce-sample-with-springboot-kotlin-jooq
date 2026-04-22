-- =====================================================================
-- V1__create_products.sql
-- 상품 마스터 테이블.
-- Flyway 파일명 규칙: V{버전}__{설명}.sql (언더스코어 2개 필수)
-- =====================================================================

CREATE TABLE products
(
    -- UUID 를 PK 로 사용하는 이유:
    -- 1) 멀티 인스턴스 환경에서 ID 충돌 없이 애플리케이션 레벨에서 생성 가능
    -- 2) BIGSERIAL 은 단일 DB 시퀀스에 의존 → 샤딩/분산 환경 확장 시 병목
    -- gen_random_uuid() 는 PostgreSQL 13+ 내장 함수 (extension 불필요)
    id          UUID PRIMARY KEY        DEFAULT uuid_generate_v7(),

    -- 상품명: 200자면 한글 기준 약 200글자, 실무 대부분 커버.
    name        VARCHAR(200)   NOT NULL,

    -- 상세 설명: 길이 제한 없는 TEXT. NULL 허용 — 설명 없이 등록 가능.
    description TEXT,

    -- 가격: NUMERIC(12, 2) 선택 이유:
    -- - FLOAT/DOUBLE 은 0.1 + 0.2 = 0.30000000000000004 같은 부동소수점 오차 발생
    -- - 금액 계산에서 1원이라도 차이나면 정산 오류 → 반드시 고정소수점
    -- - 12자리 = 최대 9,999,999,999.99 (약 100억 원)
    price       NUMERIC(12, 2) NOT NULL,

    -- 카테고리: VARCHAR(50)으로 제한.
    -- 별도 categories 테이블을 두지 않은 이유:
    -- Phase 1 은 CRUD 골격에 집중. 카테고리 관리가 필요해지면 Phase 에 추가.
    category    VARCHAR(50)    NOT NULL,

    -- 상태: DB Enum 대신 VARCHAR 를 선택한 이유:
    -- PostgreSQL ENUM 은 ALTER TYPE ADD VALUE 후 트랜잭션 내 사용 불가 등
    -- 마이그레이션 제약이 크다. 문자열로 저장하고 애플리케이션에서 enum 매핑.
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',

    -- TIMESTAMPTZ: 타임존 정보 포함.
    -- TIMESTAMP (without tz) 를 쓰면 서버 타임존 변경 시 데이터 의미가 달라진다.
    -- 분산 환경에서는 반드시 TIMESTAMPTZ + UTC 저장이 원칙.
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ── 인덱스 ──

-- 카테고리별 상품 목록 조회 (GET /api/products?category=food)에서 사용.
-- 이 인덱스가 없으면 매 조회마다 전체 테이블을 스캔한다 (Sequential Scan).
CREATE INDEX idx_products_category ON products (category);

-- 비활성 상품 필터링 (ACTIVE 상품만 노출).
-- WHERE status = 'ACTIVE' 조건이 거의 모든 목록 쿼리에 포함되므로 인덱스 효과 크다.
CREATE INDEX idx_products_status ON products (status);

-- 커서 기반 페이지네이션의 정렬 키.
-- ORDER BY created_at DESC + WHERE created_at < ? 패턴에서
-- 이 인덱스가 있어야 Index Scan 으로 처리된다.
-- DESC 로 생성하는 이유: 최신순 조회가 기본 정렬이므로 인덱스 방향 일치.
CREATE INDEX idx_products_created ON products (created_at DESC);
