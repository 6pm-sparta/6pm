// =====================================================================
// 6pm 선착순 예매 부하 테스트 (k6) — reservation.js
// 대상: 대기열 진입 → 순번 조회  (10만명 트래픽이 몰리는 핵심 구간)
// 경로/요청값은 postman/collections 에서 그대로 추출.
// 실행:  k6 run -e SHOW_ID=<시드된 showId> -e USER_COUNT=200 reservation.js
// =====================================================================
import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ── 설정 (env 로 덮어쓰기) ───────────────────────────────────────────
const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';   // 게이트웨이(8080) 경유
const SHOW_ID    = __ENV.SHOW_ID    || 'PUT-SEEDED-SHOW-ID';      // ★ 시딩된 공연 ID 로 교체
const USER_COUNT = parseInt(__ENV.USER_COUNT || '200');          // 토큰 풀 크기(가상유저용)
const PASSWORD   = __ENV.PASSWORD   || 'Test1234!';
const SMOKE      = __ENV.SMOKE === '1';   // -e SMOKE=1 이면 소량 스모크, 아니면 본 부하
const JSON_HDR   = { headers: { 'Content-Type': 'application/json' } };

// ── 부하 시나리오 ────────────────────────────────────────────────────
// 처음엔 ramp 만 켜고 baseline. spike 는 따로 돌려 비교(동시에 돌리면 해석 어려움).
export const options = {
    scenarios: {
        ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            // SMOKE=1 → 5명 20초만(점검용). 아니면 본 부하(50→200 램프).
            stages: SMOKE
                ? [ { duration: '20s', target: 5 } ]
                : [
                    { duration: '30s', target: 50 },
                    { duration: '1m',  target: 200 },
                    { duration: '1m',  target: 200 },   // 정상상태 유지(측정 구간)
                    { duration: '30s', target: 0 },
                ],
            gracefulRampDown: '10s',
        },
        // spike: {
        //   executor: 'ramping-vus', startTime: '4m', startVUs: 0,
        //   stages: [ { duration: '10s', target: 500 }, { duration: '30s', target: 500 }, { duration: '10s', target: 0 } ],
        // },
    },
    // SLO (계획 기준): 에러율<1%, p99<500ms. setup 요청은 scenario 태그가 없어 자동 제외됨.
    // 스모크에선 threshold 끔(샘플 적어 빨강 떠도 의미 없음 → checks 만 보면 됨).
    thresholds: SMOKE ? {} : {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{scenario:ramp}': ['p(99)<500', 'p(95)<300'],
    },
};

// ── setup(): 테스트 유저 N명 가입+로그인 → 토큰 풀 1회 생성 ──────────────
export function setup() {
    const tokens = [];
    const stamp = Date.now();
    for (let i = 0; i < USER_COUNT; i++) {
        const email = `loadtest_${stamp}_${i}@test.com`;
        // 회원가입 (POST /api/v1/members) — 이미 있으면 무시
        http.post(`${BASE_URL}/api/v1/members`, JSON.stringify({
            email, password: PASSWORD, nickname: `lt${i}`,
            zipCode: '12345', address1: '서울시 강남구', address2: '101호',
        }), JSON_HDR);
        // 로그인 (POST /api/v1/auth/login) → data.accessToken
        const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email, password: PASSWORD,
        }), JSON_HDR);
        const token = res.json('data.accessToken');
        if (token) tokens.push(token);
    }
    if (tokens.length === 0) {
        throw new Error('토큰 발급 0개 — 회원가입/로그인 경로(게이트웨이·auth·user) 기동 여부 확인');
    }
    console.log(`발급된 토큰: ${tokens.length}/${USER_COUNT}`);
    return { tokens };
}

// ── 가상 유저 1회 행동: 대기열 진입 → 순번 조회 ───────────────────────
export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth  = { headers: { Authorization: `Bearer ${token}` } };

    group('queue_enter', () => {
        // POST /api/v1/tickets/shows/{showId}/queue
        const r = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, auth);
        check(r, { '대기열 진입 200': (x) => x.status === 200 });
    });

    group('queue_status', () => {
        // GET /api/v1/tickets/shows/{showId}/queue/status  → data.rank, data.isReady
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, auth);
        check(r, {
            '순번 조회 200': (x) => x.status === 200,
            'rank 존재':     (x) => x.json('data.rank') !== undefined,
        });
    });

    sleep(1); // 실제 유저처럼 폴링 간격
}

// =====================================================================
// (선택) 풀 구매 경로 — 좌석목록→선점→결제 까지.
// ※ 주의: 좌석은 유한 자원이라 고VU 로 돌리면 금방 소진/실패(funnel).
//   순수 부하가 아니라 "선점 동시성/오버부킹 0" 검증용으로 별도 저VU 로 돌릴 것.
//   또한 hold 는 큐를 통과해 '구매 토큰'을 받은 유저만 성공(스케줄러/Redis 세팅 필요).
// ---------------------------------------------------------------------
// function purchaseFlow(auth) {
//   // 1) 좌석 목록: GET /api/v1/tickets/shows/{showId}/seats  → data[].seatId/status(AVAILABLE)
//   const seats = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, auth);
//   const seat  = (seats.json('data') || []).find(s => s.status === 'AVAILABLE');
//   if (!seat) return;
//   // 2) 좌석 선점: POST /api/v1/tickets/shows/{showId}/seats/{seatId}/hold → data.orderId
//   const hold = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${seat.seatId}/hold`, null, auth);
//   const orderId = hold.json('data.orderId');
//   if (!orderId) return;
//   // 3) 결제: POST /api/v1/payments  (헤더 Idempotency-Key 필수)
//   http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({ orderId, paymentMethod: 'CARD' }), {
//     headers: { ...auth.headers, 'Content-Type': 'application/json', 'Idempotency-Key': `k6-${__VU}-${__ITER}` },
//   });
// }