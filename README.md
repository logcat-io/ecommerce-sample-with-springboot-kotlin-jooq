# ecommerce-server

> Kotlin / Spring Boot 4 기반 실시간 커머스 플랫폼 포트폴리오.  
> **타임딜 동시성 제어**를 핵심 주제로, 대규모 트래픽 스파이크에서 Oversell 0 을 보장하는 재고 차감 파이프라인을 구현합니다.

---

## 구현 현황

| Phase | 주제 | 상태 |
|-------|------|------|
| Phase 1 | 상품 도메인 — CRUD, 레이어드 아키텍처 기반 | ✅ 완료 |
| Phase 2 | 타임딜 동시성 — Redis Lua + Optimistic Lock + UPSERT 3중 방어 | ✅ 완료 |
| Phase 3 | 주문 실시간 추적 — Transactional Outbox + Kafka + SSE | 🚧 예정 |

---

## 핵심 설계 — Phase 2 동시성 파이프라인

1,000+ 동시 요청에서 **Oversell 0 건**을 보장하는 3중 방어 구조입니다.

```
요청 ──→ [1차] Redis Lua 원자 차감
           │ 재고 부족? → 즉시 409 (DB 접근 0)
           ▼
         [2차] DB Optimistic Lock UPDATE
           │ version 불일치? → Redis 복구 + 409
           │ (최대 3회 재시도: version 재조회 후 재시도)
           ▼
         [3차] UPSERT WHERE quantity + ? <= maxPerUser
           │ 한도 초과? → Redis 복구 + 409
           ▼
         구매 확정
```

**왜 이 구조인가?**

- `SELECT FOR UPDATE` (비관적 락) 는 고트래픽에서 lock wait 가 DB 커넥션 풀을 고갈시킵니다.
- Redis Lua 가 90%+ 요청을 DB 도달 전에 차단하므로 Optimistic Lock 의 충돌 빈도가 낮아집니다.
- 각 레이어가 독립적으로 정합성을 보장하므로, Redis 장애나 프로세스 크래시에도 Oversell 이 발생하지 않습니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin 2.2 / JDK 21 (Virtual Thread) |
| Framework | Spring Boot 4 (Spring MVC) |
| DB | PostgreSQL 17, Flyway |
| ORM | jOOQ 3.19 (코드 생성 기반 타입 안전 쿼리) |
| Cache | Redis 7 (Lua 스크립트 원자 연산) |
| Messaging | Apache Kafka 3.8 (KRaft, Phase 3 예정) |
| Test | JUnit 5, Testcontainers, Mockito-Kotlin |
| Load Test | k6 |

---

## 프로젝트 구조

```
src/main/kotlin/org/ecommerce/
├── core/                           # 도메인 — Spring 의존성 없음
│   ├── product/
│   │   ├── model/                  # Product, ProductStatus
│   │   └── port/                   # ProductCommandPort, ProductQueryPort
│   └── timedeal/
│       ├── model/                  # TimeDeal, TimeDealStatus
│       ├── service/                # TimeDealValidator, StockDecreaser, StockRollbackHandler
│       ├── port/                   # StockPort, TimeDealCommandPort, TimeDealQueryPort
│       └── exception/              # StockExhaustedException, PurchaseLimitExceededException, ...
│
├── application/                    # UseCase 오케스트레이션
│   ├── product/usecase/            # CreateProduct, GetProduct, SearchProducts, ...
│   └── timedeal/
│       ├── usecase/                # PurchaseTimeDealUseCase, CreateTimeDealUseCase
│       ├── dto/                    # Command / Result DTO
│       └── config/                 # TimeDealBeanConfig (도메인 서비스 Bean 등록)
│
└── external/                       # 인프라 — DB, Redis, Kafka, HTTP
    ├── persistence/jooq/
    │   ├── product/                # ProductJooqAdapter
    │   └── timedeal/               # TimeDealJooqAdaptor
    ├── cache/timedeal/             # StockRedisAdaptor (Lua 스크립트)
    ├── scheduler/                  # StockReconciler (Redis-DB 정합성 복구)
    └── web/api/controller/
        ├── product/v1/             # ProductController
        └── timedeal/v1/            # TimeDealController

src/test/kotlin/org/ecommerce/
├── core/timedeal/service/
│   ├── TimeDealValidatorTest.kt    # 순수 Kotlin 단위 테스트
│   └── StockDecreaserTest.kt       # Mock 기반 재시도 정책 검증
└── core/integration/timedeal/
    └── PurchaseTimeDealConcurrencyTest.kt  # Testcontainers 동시성 통합 테스트
```

---

## 시작하기

### 사전 요구사항

- JDK 21
- Docker / Docker Compose
- k6 (부하 테스트 실행 시)

### 인프라 기동

```bash
docker compose up -d
```

PostgreSQL(5437), Redis(6378), Kafka(9092) 가 함께 기동됩니다.

### 빌드 및 실행

```bash
# jOOQ 메타모델 생성 (DB 기동 후 최초 1회)
./gradlew generateJooq

# 애플리케이션 실행 (Flyway 마이그레이션 자동 적용)
./gradlew bootRun
```

### 테스트 실행

```bash
# 단위 테스트
./gradlew test

# 동시성 통합 테스트 (Testcontainers — 약 30~60초 소요)
./gradlew test --tests "*.PurchaseTimeDealConcurrencyTest"
```

---

## API 엔드포인트

### 상품 (Phase 1)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/products` | 상품 생성 |
| `GET` | `/api/products/{id}` | 상품 단건 조회 |
| `GET` | `/api/products?keyword=&category=` | 상품 검색 |
| `PUT` | `/api/products/{id}` | 상품 수정 |
| `DELETE` | `/api/products/{id}` | 상품 삭제 |

### 타임딜 (Phase 2)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/v1/time-deals` | 타임딜 생성 |
| `POST` | `/api/v1/time-deals/{id}/purchase` | 타임딜 구매 |

---

## 타임딜 동작 확인

### 타임딜 생성

```bash
# 1. 상품 생성
PRODUCT_ID=$(curl -s -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "한정판 스니커즈",
    "description": "타임딜 테스트용",
    "price": 150000,
    "category": "shoes"
  }' | jq -r '.data.id')

# 2. 타임딜 생성 (재고 100, 1인 1개 한도)
TIME_DEAL_ID=$(curl -s -X POST http://localhost:8080/api/v1/time-deals \
  -H 'Content-Type: application/json' \
  -d "{
    \"productId\": \"${PRODUCT_ID}\",
    \"dealPrice\": 99000,
    \"originalPrice\": 150000,
    \"totalStock\": 100,
    \"maxPerUser\": 1,
    \"startAt\": \"2024-01-01T00:00:00Z\",
    \"endAt\": \"2030-12-31T23:59:59Z\"
  }" | jq -r '.data.id')

echo "TIME_DEAL_ID: $TIME_DEAL_ID"
```

### 구매 요청

```bash
curl -s -X POST http://localhost:8080/api/v1/time-deals/${TIME_DEAL_ID}/purchase \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "00000000-0000-0000-0000-000000000001",
    "quantity": 1
  }'
```

### k6 부하 테스트 (Oversell 검증)

```bash
k6 run \
  -e TIME_DEAL_ID="${TIME_DEAL_ID}" \
  -e BASE_URL="http://localhost:8080" \
  scripts/time-deal-spike.js
```

정상 결과:
```
=== Oversell Detection Report ===
Purchase Success: 100
Purchase Failed:  100
Oversell:         NO ✅
=================================
✓ status is 201 or 409  100.00%
```

---

## 주요 설계 결정

### 왜 Lua 스크립트인가?

`DECRBY` 단독으로는 "확인 후 차감" 을 원자적으로 처리할 수 없습니다. GET → 판단 → DECRBY 사이에 다른 요청이 끼어들면 재고가 음수가 됩니다. Lua 스크립트는 이 세 단계를 Redis 서버 내부에서 단일 명령으로 실행합니다.

### 왜 Optimistic Lock인가?

비관적 락(`SELECT FOR UPDATE`)은 동일 row에 대한 모든 요청을 순차 대기시킵니다. 1,000 req/s 스파이크에서는 lock wait 가 DB 커넥션 풀을 고갈시킵니다. Optimistic Lock은 잠금 없이 UPDATE 시점에 version 불일치를 감지합니다. Redis가 이미 90%+ 를 사전 차단하므로 DB 도달 요청 수가 적어 충돌 빈도가 낮습니다.

### 왜 재시도(MAX_RETRY_COUNT=3)인가?

Redis를 통과한 소수의 요청이 동시에 동일 version을 읽으면, 1건만 DB OL에 성공하고 나머지는 실패합니다. 이는 Oversell이 아니라 기회 손실입니다. 재시도는 version을 매 시도마다 fresh하게 읽어 정상 요청의 실패를 최소화합니다.

### StockReconciler

프로세스 크래시 또는 rollback 실패로 `Redis < DB` 불일치가 발생할 수 있습니다. `StockReconciler`가 60초 주기로 ACTIVE 딜 전체를 순회하며 `INCRBY` 로 복구합니다. `SET` 대신 `INCRBY` 를 쓰는 이유는 복구 시점에 in-flight 요청이 동시에 차감 중일 수 있기 때문입니다.
