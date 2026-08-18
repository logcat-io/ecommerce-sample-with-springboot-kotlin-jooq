#!/usr/bin/env bash
# 커넥션 풀 크기를 바꿔 가며 경계가 어떻게 이동하는지 본다.
#
# 답하려는 것:
#   1. 병목이 정말 커넥션 풀인가 — 경계가 풀 크기를 따라 움직이는가
#   2. 풀만 늘리면 Redis 없이도 되는가
#   3. OL 재시도가 자기 증폭하는가 — 풀이 커지면 충돌이 늘어나는가
set -euo pipefail
cd "$(dirname "$0")/.."

POOL="${POOL:-20}"
PEAK="${PEAK:-1500}"
JAR=build/libs/ecommerce-0.0.1-SNAPSHOT.jar

pkill -f "$JAR" 2>/dev/null || true
sleep 2
nohup java -jar "$JAR" --spring.datasource.hikari.maximum-pool-size="$POOL" > /tmp/app-pool.log 2>&1 &
for _ in $(seq 1 60); do
  curl -s -o /dev/null http://localhost:8080/actuator/health 2>/dev/null && break
  sleep 1
done
sleep 3

ACTUAL=$(curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.max \
  | python3 -c "import json,sys;print(int(json.load(sys.stdin)['measurements'][0]['value']))")
echo "── pool=$ACTUAL  peak=${PEAK}rps ──"

# 스파이크 동안 풀 상태를 200ms 간격으로 샘플링해 최댓값을 남긴다.
poll() {
  : > /tmp/pool-samples.txt
  for _ in $(seq 1 90); do
    curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active 2>/dev/null \
      | python3 -c "import json,sys;print(json.load(sys.stdin)['measurements'][0]['value'])" 2>/dev/null >> /tmp/pool-samples.txt || true
    curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending 2>/dev/null \
      | python3 -c "import json,sys;print('P'+str(json.load(sys.stdin)['measurements'][0]['value']))" 2>/dev/null >> /tmp/pool-samples.txt || true
    sleep 0.2
  done
}

run() {
  local mode=$1 label=$2
  poll & local pid=$!
  MODE=$mode PEAK=$PEAK HOLD=3 DURATION=16 SPIKE_START=6 ./scripts/run-timedeal-test.sh > /tmp/r.txt 2>&1
  kill $pid 2>/dev/null || true; wait $pid 2>/dev/null || true
  sleep 1

  local td
  td=$(docker exec "$(docker compose ps -q postgres)" psql -U ecommerce -d ecommerce -t -c \
    "SELECT seq_scan+idx_scan FROM pg_stat_user_tables WHERE relname='time_deals';" 2>/dev/null | xargs)

  POOL=$ACTUAL LABEL="$label" TD="$td" python3 - <<'PY'
import json, os
d = json.load(open('reports/last-run-summary.json'))['metrics']
g = lambda n, f='count': d.get(n, {}).get('values', {}).get(f, 0)
sub = lambda p, f: d.get(f'browse_duration{{phase:{p}}}', {}).get('values', {}).get(f, 0)

req = int(g('purchase_success') + g('purchase_sold_out') + g('purchase_version_conflict') + g('purchase_5xx'))
before, during = sub('before', 'p(95)'), sub('during', 'p(95)')

act = pend = 0.0
try:
    for line in open('/tmp/pool-samples.txt'):
        line = line.strip()
        if not line: continue
        if line.startswith('P'): pend = max(pend, float(line[1:]))
        else: act = max(act, float(line))
except FileNotFoundError:
    pass

td = int(os.environ['TD'] or 0)
print(f"  {os.environ['LABEL']:<10} 조회p95 {before:6.2f}→{during:8.2f} ({during/before if before else 0:6.2f}배) | "
      f"구매p95 {g('purchase_duration','p(95)'):8.2f} | DB {td/req if req else 0:5.2f}회/req | "
      f"active최대 {act:4.0f}/{os.environ['POOL']} pending최대 {pend:4.0f} | "
      f"성공 {int(g('purchase_success'))} 충돌 {int(g('purchase_version_conflict')):4d}")
PY
}

run filtered   "워밍업" > /dev/null 2>&1 || true
run filtered   "ON#1"
run unfiltered "OFF#1"
run filtered   "ON#2"
run unfiltered "OFF#2"
