// k6 import 는 반드시 파일 최상단에 위치해야 한다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

const purchaseSuccess  = new Counter('purchase_success');
const purchaseFailed   = new Counter('purchase_failed');
const purchaseDuration = new Trend('purchase_duration', true);

export const options = {
    scenarios: {
        spike: {
            executor: 'shared-iterations',
            vus: 2000,         // 동시 가상 사용자 200명
            iterations: 2000,  // 총 200번 (각 VU 가 1번)
            maxDuration: '30s',
        },
    },
    thresholds: {
        'purchase_success':  ['count==100'],   // 재고(100)와 정확히 일치
        'purchase_duration': ['p(95)<300'],    // 95% 요청이 200ms 미만
    },
};

// 테스트 전 curl 로 딜을 생성하고 TIME_DEAL_ID 를 환경변수로 주입한다.
// 실행 예시: k6 run -e TIME_DEAL_ID=<uuid> scripts/time-deal-spike.js
const TIME_DEAL_ID = __ENV.TIME_DEAL_ID;
const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8080';

if (!TIME_DEAL_ID) {
    throw new Error('TIME_DEAL_ID 환경변수가 필요합니다. -e TIME_DEAL_ID=<uuid> 로 전달하세요.');
}

export default function () {
    const payload = JSON.stringify({
        userId:   generateUUIDv7(),
        quantity: 1,
    });

    const res = http.post(
        `${BASE_URL}/api/v1/time-deals/${TIME_DEAL_ID}/purchase`,
        payload,
        { headers: { 'Content-Type': 'application/json' } },
    );

    purchaseDuration.add(res.timings.duration);

    if (res.status === 201) {
        purchaseSuccess.add(1);
    } else {
        purchaseFailed.add(1);
    }

    check(res, {
        'status is 201 or 409 or 500': (r) => r.status === 201 || r.status === 409 || r.status === 500,
    });
}

export function handleSummary(data) {
    const successCount = data.metrics.purchase_success?.values.count ?? 0;
    const failedCount  = data.metrics.purchase_failed?.values.count  ?? 0;
    const oversell     = successCount > 100;

    console.log('=== Oversell Detection Report ===');
    console.log(`Purchase Success: ${successCount}`);
    console.log(`Purchase Failed:  ${failedCount}`);
    console.log(`Oversell:         ${oversell ? 'YES ⚠️' : 'NO ✅'}`);
    console.log('=================================');

    return {
        stdout: textSummary(data, { indent: '  ', enableColors: true }),
    };
}

// UUID v7: 48비트 ms 타임스탬프 + 버전(7) + variant(10xx) + 랜덤
// 형식: tttttttt-tttt-7rrr-vrrr-rrrrrrrrrrrr
function generateUUIDv7() {
    const now = Date.now();
    const t   = now.toString(16).padStart(12, '0');

    const rand = (hexLen) =>
        Array.from({ length: hexLen }, () =>
            Math.floor(Math.random() * 16).toString(16)
        ).join('');

    const randA   = rand(3);                                          // 12비트
    const variant = (0x8 | (Math.random() * 4 | 0)).toString(16);   // 10xx variant
    const randB   = rand(15);                                         // 60비트

    return `${t.slice(0, 8)}-${t.slice(8, 12)}-7${randA}-${variant}${randB.slice(0, 3)}-${randB.slice(3, 15)}`;
}
