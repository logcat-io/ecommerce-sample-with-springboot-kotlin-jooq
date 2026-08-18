// 타임딜 오픈 스파이크 — 도착률 기반 + 경쟁 트래픽 동시 측정
//
// 이 프로젝트의 주장은 "확정 실패할 요청이 커넥션을 붙들어 무관한 요청을 밀어낸다" 이다.
// 그걸 확인하려면 밀려나는 쪽을 같이 재야 한다. 그래서 상품 조회를 전 구간에
// 일정 도착률로 흘리고, 중간에 타임딜 스파이크를 얹어 조회 지연 변화를 본다.
//
// 파라미터를 정한 근거:
//   재고 100 / 요청 2,000  → 초과청약 20:1. 이 설계가 존재하는 이유가 이 비율이다.
//   ramping-arrival-rate   → 타임딜은 "2,000명이 앉아 있는" 게 아니라 "오픈 순간 요청이
//                            쏟아지는" 것이다. VU 기반은 클라이언트가 먼저 병목이 된다.
//   스파이크 4초           → 선착순은 초 단위로 끝난다. 55초 지속은 공성전이지 타임딜이 아니다.
//   조회 50 rps           → 타임딜과 무관한 정상 트래픽. 이 지연이 주 지표다.

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STOCK    = Number(__ENV.STOCK || 100);
const PEAK     = Number(__ENV.PEAK || 700);      // 스파이크 피크 도착률 (rps)
const HOLD     = Number(__ENV.HOLD || 2);        // 피크 유지 시간 (초)
const BROWSE   = Number(__ENV.BROWSE || 50);     // 경쟁 트래픽 도착률 (rps)
const DURATION = Number(__ENV.DURATION || 25);   // 전체 측정 시간 (초)

const SPIKE_START = Number(__ENV.SPIKE_START || 10);
const SPIKE_END   = SPIKE_START + HOLD + 2;      // 램프 업/다운 각 1초 포함

const purchaseSuccess = new Counter('purchase_success');
const purchaseSoldOut = new Counter('purchase_sold_out');
const purchaseConflict= new Counter('purchase_version_conflict');
const purchase5xx     = new Counter('purchase_5xx');
const purchaseOther   = new Counter('purchase_other');
const purchaseDur     = new Trend('purchase_duration', true);
const browseDur       = new Trend('browse_duration', true);
const browseFail      = new Counter('browse_failed');
const browseBlocked   = new Trend('browse_blocked', true);   // 커넥션 확보 대기 — 오르면 k6 쪽 병목 의심

export const options = {
    scenarios: {
        browse: {
            executor: 'constant-arrival-rate',
            rate: BROWSE, timeUnit: '1s',
            duration: `${DURATION}s`,
            preAllocatedVUs: 40, maxVUs: 600,
            exec: 'browse',
        },
        spike: {
            executor: 'ramping-arrival-rate',
            startTime: `${SPIKE_START}s`,
            startRate: 0, timeUnit: '1s',
            preAllocatedVUs: 200, maxVUs: 4000,
            stages: [
                { target: PEAK, duration: '1s' },        // 오픈 순간
                { target: PEAK, duration: `${HOLD}s` },  // 피크 유지
                { target: 0,    duration: '1s' },        // 소진 후 급감
            ],
            exec: 'purchase',
        },
    },
    // 구간별 조회 지연을 서브메트릭으로 만들기 위한 선언 (항상 통과하는 임계값)
    thresholds: {
        'browse_duration{phase:before}': ['p(95)>=0'],
        'browse_duration{phase:during}': ['p(95)>=0'],
        'browse_duration{phase:after}':  ['p(95)>=0'],
        'browse_blocked{phase:during}':  ['p(95)>=0'],
        'purchase_success':              [`count<=${STOCK}`],   // Oversell 가드
    },
};

export function setup() {
    // 딜을 외부에서 준비한 경우(상태를 ACTIVE 로 올려야 하므로 보통 이 경로) 그대로 쓴다.
    if (__ENV.PRODUCT_ID && __ENV.TIME_DEAL_ID) {
        console.log(`외부 준비된 딜 사용: deal=${__ENV.TIME_DEAL_ID} stock=${STOCK}`);
        return { productId: __ENV.PRODUCT_ID, timeDealId: __ENV.TIME_DEAL_ID };
    }
    const p = http.post(`${BASE_URL}/api/v1/products`, JSON.stringify({
        name: '타임딜 부하 측정용', description: null, price: 150000, category: 'loadtest',
    }), { headers: { 'Content-Type': 'application/json' } });
    const productId = p.json('data.id');

    const now = Date.now();
    const d = http.post(`${BASE_URL}/api/v1/time-deals`, JSON.stringify({
        productId,
        dealPrice: 99000, originalPrice: 150000,
        totalStock: STOCK, maxPerUser: 1,
        startAt: new Date(now - 60_000).toISOString(),
        endAt:   new Date(now + 3_600_000).toISOString(),
    }), { headers: { 'Content-Type': 'application/json' } });

    const timeDealId = d.json('data.id');
    if (!productId || !timeDealId) {
        throw new Error(`setup 실패 product=${p.status} deal=${d.status} body=${d.body}`);
    }
    console.log(`setup: product=${productId} deal=${timeDealId} stock=${STOCK}`);
    return { productId, timeDealId };
}

function phase() {
    const t = exec.instance.currentTestRunDuration / 1000;
    if (t < SPIKE_START) return 'before';
    if (t < SPIKE_END)   return 'during';
    return 'after';
}

export function browse(data) {
    const res = http.get(`${BASE_URL}/api/v1/products/${data.productId}`, {
        tags: { endpoint: 'browse' },
    });
    const ph = phase();
    browseDur.add(res.timings.duration, { phase: ph });
    browseBlocked.add(res.timings.blocked, { phase: ph });
    if (res.status !== 200) browseFail.add(1);
    check(res, { 'browse 200': (r) => r.status === 200 });
}

export function purchase(data) {
    const res = http.post(
        `${BASE_URL}/api/v1/time-deals/${data.timeDealId}/purchase`,
        JSON.stringify({ userId: uuidv7(), quantity: 1 }),
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'purchase' } },
    );
    purchaseDur.add(res.timings.duration);

    if (res.status === 201)      purchaseSuccess.add(1);
    else if (res.status === 409) {
        const code = res.json('error.code') || '';
        if (code === 'STOCK_VERSION_CONFLICT') purchaseConflict.add(1);
        else purchaseSoldOut.add(1);
    }
    else if (res.status >= 500)  purchase5xx.add(1);
    else                         purchaseOther.add(1);

    check(res, { 'purchase 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function handleSummary(data) {
    const g = (n, f = 'count') => data.metrics[n]?.values?.[f] ?? 0;
    const sub = (p, f) => data.metrics[`browse_duration{phase:${p}}`]?.values?.[f] ?? 0;

    const lines = [
        '',
        '=========== 타임딜 오픈 스파이크 측정 ===========',
        `재고 ${STOCK} / 구매 요청 ${g('purchase_success') + g('purchase_sold_out') + g('purchase_version_conflict') + g('purchase_5xx') + g('purchase_other')}`,
        '',
        '[정합성]',
        `  구매 성공        ${g('purchase_success')}  (재고와 일치해야 함: ${STOCK})`,
        `  Oversell         ${g('purchase_success') > STOCK ? 'YES' : 'NO'}`,
        `  409 품절         ${g('purchase_sold_out')}`,
        `  409 버전충돌     ${g('purchase_version_conflict')}`,
        `  5xx              ${g('purchase_5xx')}`,
        `  기타 상태        ${g('purchase_other')}`,
        '',
        '[밀려나는 쪽 — 상품 조회 p95]',
        `  스파이크 전      ${sub('before', 'p(95)').toFixed(1)} ms   (n=${sub('before', 'count')})`,
        `  스파이크 중      ${sub('during', 'p(95)').toFixed(1)} ms   (n=${sub('during', 'count')})`,
        `  스파이크 후      ${sub('after',  'p(95)').toFixed(1)} ms   (n=${sub('after',  'count')})`,
        `  조회 실패        ${g('browse_failed')}`,
        '',
        '[구매 응답]',
        `  avg ${g('purchase_duration', 'avg').toFixed(1)} ms / p95 ${g('purchase_duration', 'p(95)').toFixed(1)} ms / max ${g('purchase_duration', 'max').toFixed(1)} ms`,
        '===============================================',
        '',
    ];
    console.log(lines.join('\n'));

    return {
        stdout: textSummary(data, { indent: '  ', enableColors: true }),
        'reports/last-run-summary.json': JSON.stringify(data, null, 2),
    };
}

function uuidv7() {
    const t = Date.now().toString(16).padStart(12, '0');
    const r = (n) => Array.from({ length: n }, () => Math.floor(Math.random() * 16).toString(16)).join('');
    const variant = (0x8 | (Math.random() * 4 | 0)).toString(16);
    const b = r(15);
    return `${t.slice(0, 8)}-${t.slice(8, 12)}-7${r(3)}-${variant}${b.slice(0, 3)}-${b.slice(3, 15)}`;
}
