// order-loadtest.js — 주문생성(order-service 직접) → 결제요청(게이트웨이 경유) → 결과 폴링
//
// [실행 전 반드시 확인]
// 1. ORDER_DIRECT_URL 포트 (기본 8084) — order-service 실제 포트와 다르면 -e로 교체
// 2. 회원가입(/api/v1/members) 응답이 data.userId 형태가 아니면 setup() 파싱 로직 수정
// 3. order-service를 CHAOS_MODE_ENABLED=true로 띄울 것 (안 켜면 전부 APPROVED만 나옴)
//
// 알아둘 것:
// - seatId는 매 이터레이션 새로 생성함 (uq_orders_seat_active 부분 UNIQUE 인덱스 때문에
//   재사용하면 두 번째 요청부터 전부 충돌 → 가짜 실패로 잡힘)
// - holdId, Idempotency-Key도 매번 새로 생성 (재사용 시 멱등 캐시 응답만 돌아와서 실제 로직 안 탐)
// - Idempotency-Key에 접두어 안 붙이면 chaos 모드 대상이 됨 (기본: 실패 3% / webhook 유실 2% / 지연 10%)
// - 결제 승인은 비동기(webhook)라서 POST /payments 응답은 APPROVED가 아니라 REQUESTED로 옴.
//   base delay 1500ms + SLOW 지터 최대 3000ms = 최악 4.5초 걸리므로, 6.5초 버퍼로 폴링해서 최종 상태 확인.
// - webhook 유실(2%) 케이스는 폴링 끝나도 REQUESTED로 남음 — 이건 버그가 아니라 실제 좀비 결제 재현
//   (zombie-payment-recovery 배치가 15초 주기로 나중에 처리하는 그 케이스)
// - USER_COUNT명 만큼 setup()에서 회원가입+로그인 하므로, PEAK을 크게 올릴 땐 USER_COUNT도 같이 올릴 것
//   (유저 풀보다 VU가 훨씬 많으면 동일 유저가 여러 VU에서 겹쳐 쓰이지만, 동시성 자체엔 문제 없음)

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 실제 결제 결과 기준 커스텀 메트릭 (HTTP status만으론 SLO-4를 못 봄 — 비동기라 접수 성공과 실제 승인이 다름)
const paymentApproved = new Counter('payment_approved');
const paymentFailed   = new Counter('payment_failed');
const paymentTimeout  = new Counter('payment_timeout_or_lost'); // 폴링 끝까지 REQUESTED로 남음 (webhook 유실 후보)
const paymentSuccessRate = new Rate('payment_success_rate');

const GATEWAY_URL      = __ENV.GATEWAY_URL      || 'http://localhost:8080';
const ORDER_DIRECT_URL = __ENV.ORDER_DIRECT_URL || 'http://localhost:8084';
const PEAK             = parseInt(__ENV.PEAK || '300');
const USER_COUNT       = parseInt(__ENV.USER_COUNT || '200');
const SLEEP            = parseFloat(__ENV.SLEEP || '0.3');
const PASSWORD         = __ENV.PASSWORD || 'Test1234!';
const JSON_HDR         = { headers: { 'Content-Type': 'application/json' } };

export const options = {
    scenarios: {
        load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: Math.round(PEAK * 0.4) },
                { duration: '1m',  target: PEAK },
                { duration: '2m',  target: PEAK },   // 측정 구간
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{group:::주문생성}': ['p(99)<500'],
        'http_req_duration{group:::결제요청}': ['p(99)<1000'],
        // chaos 기본값(실패3%+유실2%) 기준 기대 성공률 95% 근처. 여유 두고 90%로 sanity check만.
        payment_success_rate: ['rate>0.90'],
    },
};

// 유저 N명 회원가입+로그인 → { token, userId } 풀 생성
export function setup() {
    const users = [];
    const stamp = Date.now();

    for (let i = 0; i < USER_COUNT; i++) {
        const email = `lt_${stamp}_${i}@test.com`;

        const signupRes = http.post(`${GATEWAY_URL}/api/v1/members`, JSON.stringify({
            email,
            password: PASSWORD,
            nickname: `lt${stamp}_${i}`.slice(0, 20), // nickname도 stamp 붙여야 실행 간 중복 안 남 (길이 제한 있을 수 있어 20자로 컷)
            zipCode: '54321',
            address1: '서울시 마포구',
            address2: '202호',
        }), JSON_HDR);

        const userId = signupRes.json('data.userId');
        if (!userId) {
            console.log(`회원가입 실패 email=${email} body=${signupRes.body}`);
            continue;
        }

        const loginRes = http.post(`${GATEWAY_URL}/api/v1/auth/login`, JSON.stringify({
            email, password: PASSWORD,
        }), JSON_HDR);

        const token = loginRes.json('data.accessToken');
        if (!token) {
            console.log(`로그인 실패 email=${email} body=${loginRes.body}`);
            continue;
        }

        users.push({ token, userId });
    }

    if (!users.length) throw new Error('유저 0명 확보 — 회원가입/로그인 경로 또는 필드 확인');
    console.log(`유저 ${users.length}/${USER_COUNT} 확보 · PEAK=${PEAK}`);
    return { users };
}

export default function (data) {
    const user = data.users[__VU % data.users.length];
    const auth = { headers: { Authorization: `Bearer ${user.token}` } };

    const seatId = uuidv4();
    const holdId = uuidv4();
    const idemKey = uuidv4(); // 접두어 없음 → chaos 모드 대상 (기본: 실패3%/유실2%/지연10%, 나머지 APPROVED)

    let orderId = null;

    group('주문생성', () => {
        // order-service 직접 호출 (게이트웨이 우회) — 원래 Ticketing이 Feign으로 호출하는 내부 API를
        // k6가 대신 흉내내는 구조
        const res = http.post(`${ORDER_DIRECT_URL}/internal/v1/orders`, JSON.stringify({
            holdId,
            seatId,
            userId: user.userId,
            totalAmount: 50000,
        }), JSON_HDR);

        const ok = check(res, {
            '201 또는 200': (r) => r.status === 201 || r.status === 200,
        });

        if (ok) {
            orderId = res.json('data.orderId');
            if (!orderId) console.log(`주문생성 응답 파싱 실패, body=${res.body}`);
        } else if (Math.random() < 0.01) { // 실패가 많을 수 있어 1%만 샘플링
            console.log(`주문생성 실패 status=${res.status} body=${res.body}`);
        }
    });

    if (!orderId) {
        sleep(SLEEP);
        return; // 주문 생성 실패 시 결제 단계 스킵 (실패를 결제 실패로 잘못 카운트하지 않기 위함)
    }

    let paymentId = null;

    group('결제요청', () => {
        const res = http.post(`${GATEWAY_URL}/api/v1/payments`, JSON.stringify({
            orderId,
            paymentMethod: 'CARD',
        }), {
            headers: {
                ...auth.headers,
                'Content-Type': 'application/json',
                'Idempotency-Key': idemKey,
            },
        });

        const ok = check(res, {
            '201 또는 200': (r) => r.status === 201 || r.status === 200,
        });

        if (ok) {
            paymentId = res.json('data.paymentId');
            if (!paymentId) console.log(`결제요청 응답 파싱 실패, body=${res.body}`);
        } else if (Math.random() < 0.01) {
            console.log(`결제요청 실패 status=${res.status} body=${res.body}`);
        }
    });

    if (!paymentId) {
        sleep(SLEEP);
        return; // 결제 요청 자체가 실패(접수 실패) — 아래 결과 폴링과는 다른 케이스
    }

    group('결제결과확인', () => {
        sleep(1.5); // base delay(callback-delay-millis)만큼은 최소 대기 후 폴링 시작

        let status = 'REQUESTED';
        for (let i = 0; i < 5 && (status === 'REQUESTED' || status === 'PENDING'); i++) {
            const res = http.get(`${GATEWAY_URL}/api/v1/payments/${paymentId}`, auth);
            status = res.json('data.paymentStatus') || status;
            if (status === 'REQUESTED' || status === 'PENDING') sleep(1);
        }

        if (status === 'APPROVED') {
            paymentApproved.add(1);
            paymentSuccessRate.add(true);
        } else if (status === 'FAILED') {
            paymentFailed.add(1);
            paymentSuccessRate.add(false);
        } else {
            // 6.5초 버퍼(1500ms base + 최대 3000ms SLOW 지터) 지나도 REQUESTED면 webhook 유실 후보
            paymentTimeout.add(1);
            paymentSuccessRate.add(false);
        }
    });

    sleep(SLEEP);
}