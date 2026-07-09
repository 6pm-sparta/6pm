// order-direct-loadtest.js — SLO-4 결제, gateway/auth/user 전부 우회
// order-loadtest.js와 시나리오는 동일(주문생성→결제요청→결제결과폴링)하되,
// 회원가입/로그인 없이 X-Id-Card 헤더를 직접 서명해서 order-service(기본 8084) 하나만으로 검증한다.
//
// [실행 전 반드시 확인]
// 1. order-service를 -Dorder.chaos.enabled=true 로 띄울 것 (안 켜면 전부 APPROVED만 나옴)
// 2. order-service VM options에 -Dhmac.secret-key=<gateway/ticketing과 동일 값> 포함
// 3. ORDER_DIRECT_URL 포트(기본 8084)가 실제 order-service 포트와 다르면 -e로 교체
//
// 알아둘 것 (order-loadtest.js와 동일):
// - seatId/holdId/Idempotency-Key는 매 이터레이션 새로 생성 (재사용 시 uq 충돌/멱등 캐시로 가짜 결과)
// - 결제 승인은 비동기(webhook)라서 POST /payments 응답은 REQUESTED로 옴 → 6.5초 버퍼로 폴링

import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const paymentApproved = new Counter('payment_approved');
const paymentFailed   = new Counter('payment_failed');
const paymentTimeout  = new Counter('payment_timeout_or_lost');
const paymentSuccessRate = new Rate('payment_success_rate');

const ORDER_DIRECT_URL = __ENV.ORDER_DIRECT_URL || 'http://localhost:8084';
const HMAC_SECRET       = __ENV.HMAC_SECRET       || '6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long';
const PEAK              = parseInt(__ENV.PEAK || '300');
const SLEEP             = parseFloat(__ENV.SLEEP || '0.3');

// gateway가 하던 UserIdCard 서명을 k6가 대신 수행 (ticketing-direct-functional.js와 동일 로직)
function idCardHeaders(userId, role = 'MEMBER') {
    const idCardJson = JSON.stringify({ userId, role });
    const signature = crypto.hmac('sha256', HMAC_SECRET, idCardJson, 'base64');
    return {
        headers: {
            'Content-Type': 'application/json',
            'X-Id-Card': idCardJson,
            'X-Id-Card-Signature': signature,
        },
    };
}

export const options = {
    scenarios: {
        load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: Math.round(PEAK * 0.4) },
                { duration: '1m',  target: PEAK },
                { duration: '2m',  target: PEAK },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{group:::주문생성}': ['p(99)<500'],
        'http_req_duration{group:::결제요청}': ['p(99)<1000'],
        payment_success_rate: ['rate>0.90'],
    },
};

export default function () {
    const userId  = uuidv4(); // 회원가입 없이 매 이터레이션 임의 유저로 서명 (order-service는 존재 여부 검증 안 함)
    const auth    = idCardHeaders(userId);
    const seatId  = uuidv4();
    const holdId  = uuidv4();
    const idemKey = uuidv4(); // 접두어 없음 → chaos 모드 대상

    let orderId = null;

    group('주문생성', () => {
        const res = http.post(`${ORDER_DIRECT_URL}/internal/v1/orders`, JSON.stringify({
            holdId, seatId, userId, totalAmount: 50000,
        }), { headers: { 'Content-Type': 'application/json' } }); // 내부 API — 인증 불필요

        const ok = check(res, { '201 또는 200': (r) => r.status === 201 || r.status === 200 });
        if (ok) {
            orderId = res.json('data.orderId');
        } else if (Math.random() < 0.01) {
            console.log(`주문생성 실패 status=${res.status} body=${res.body}`);
        }
    });

    if (!orderId) { sleep(SLEEP); return; }

    let paymentId = null;

    group('결제요청', () => {
        const res = http.post(`${ORDER_DIRECT_URL}/api/v1/payments`, JSON.stringify({
            orderId, paymentMethod: 'CARD',
        }), {
            headers: { ...auth.headers, 'Idempotency-Key': idemKey },
        });

        const ok = check(res, { '201 또는 200': (r) => r.status === 201 || r.status === 200 });
        if (ok) {
            paymentId = res.json('data.paymentId');
        } else if (Math.random() < 0.01) {
            console.log(`결제요청 실패 status=${res.status} body=${res.body}`);
        }
    });

    if (!paymentId) { sleep(SLEEP); return; }

    group('결제결과확인', () => {
        sleep(1.5);
        let status = 'REQUESTED';
        for (let i = 0; i < 5 && (status === 'REQUESTED' || status === 'PENDING'); i++) {
            const res = http.get(`${ORDER_DIRECT_URL}/api/v1/payments/${paymentId}`, auth);
            status = res.json('data.paymentStatus') || status;
            if (status === 'REQUESTED' || status === 'PENDING') sleep(1);
        }

        if (status === 'APPROVED') {
            paymentApproved.add(1); paymentSuccessRate.add(true);
        } else if (status === 'FAILED') {
            paymentFailed.add(1); paymentSuccessRate.add(false);
        } else {
            paymentTimeout.add(1); paymentSuccessRate.add(false);
        }
    });

    sleep(SLEEP);
}
