#!/usr/bin/env bash
# 타임딜 부하 측정 러너.
#
# 딜 생성만으로 구매가 열린다. 판매 가능 여부는 TimeDeal.isActiveAt 이 시간으로
# 판단하므로 상태 라벨 전이 워커를 기다릴 필요가 없다.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
STOCK="${STOCK:-100}"
PG=$(docker compose ps -q postgres)
RD=$(docker compose ps -q redis)

# 런마다 데이터가 쌓이면 findById·getPurchasedQuantity 비용이 조금씩 달라져
# 런끼리 비교가 안 된다. 매번 같은 조건에서 시작한다.
docker exec "$PG" psql -U ecommerce -d ecommerce -q -c \
  "TRUNCATE time_deal_purchases, time_deals, products CASCADE;"
docker exec "$RD" redis-cli FLUSHDB > /dev/null

PRODUCT_ID=$(curl -s -X POST "$BASE_URL/api/v1/products" -H 'Content-Type: application/json' \
  -d '{"name":"타임딜 부하 측정용","description":null,"price":150000,"category":"loadtest"}' | jq -r '.data.id')

START=$(python3 -c "import datetime as d;print((d.datetime.now(d.timezone.utc)-d.timedelta(minutes=1)).isoformat().replace('+00:00','Z'))")
END=$(python3   -c "import datetime as d;print((d.datetime.now(d.timezone.utc)+d.timedelta(hours=1)).isoformat().replace('+00:00','Z'))")

TIME_DEAL_ID=$(curl -s -X POST "$BASE_URL/api/v1/time-deals" -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"dealPrice\":99000,\"originalPrice\":150000,\"totalStock\":$STOCK,\"maxPerUser\":1,\"startAt\":\"$START\",\"endAt\":\"$END\"}" \
  | jq -r '.data.id')

# MODE=unfiltered 는 Redis 재고를 크게 올려 1차 필터를 무력화한다.
# 코드를 고치지 않고 "Redis 없이 같은 부하를 DB 가 받으면?" 대조군을 만드는 방법이다.
# DB 재고는 그대로 $STOCK 이므로 성공 건수는 양쪽이 같아야 한다.
MODE="${MODE:-filtered}"
if [ "$MODE" = "unfiltered" ]; then
  docker exec "$RD" redis-cli SET "stock:$TIME_DEAL_ID" 99999999 > /dev/null
fi

echo "mode=$MODE  deal=$TIME_DEAL_ID  stock=$STOCK  redis=$(docker exec "$RD" redis-cli GET "stock:$TIME_DEAL_ID")"

docker exec "$RD" redis-cli CONFIG RESETSTAT > /dev/null
docker exec "$PG" psql -U ecommerce -d ecommerce -q -c "SELECT pg_stat_reset();" > /dev/null

k6 run -e BASE_URL="$BASE_URL" -e STOCK="$STOCK" \
       -e PEAK="${PEAK:-700}" -e HOLD="${HOLD:-2}" \
       -e BROWSE="${BROWSE:-50}" -e DURATION="${DURATION:-25}" \
       -e SPIKE_START="${SPIKE_START:-10}" \
       -e PRODUCT_ID="$PRODUCT_ID" -e TIME_DEAL_ID="$TIME_DEAL_ID" \
       scripts/timedeal-realistic.js

echo
echo "── 측정 후 상태 ──"
docker exec "$RD" redis-cli GET "stock:$TIME_DEAL_ID" | sed 's/^/redis 잔여: /'
docker exec "$RD" redis-cli INFO commandstats | grep -E "evalsha|decrby|incrby|cmdstat_get:" || true
docker exec "$PG" psql -U ecommerce -d ecommerce -c \
  "SELECT remaining_stock, version FROM time_deals WHERE id='$TIME_DEAL_ID';"
docker exec "$PG" psql -U ecommerce -d ecommerce -c \
  "SELECT count(*) AS purchase_rows FROM time_deal_purchases WHERE time_deal_id='$TIME_DEAL_ID';"
docker exec "$PG" psql -U ecommerce -d ecommerce -c \
  "SELECT relname, n_tup_ins, n_tup_upd, n_tup_hot_upd, idx_scan FROM pg_stat_user_tables WHERE relname IN ('time_deals','time_deal_purchases','products');"
curl -s "$BASE_URL/actuator/metrics/hikaricp.connections.active" 2>/dev/null | jq -c '.measurements' 2>/dev/null || true
