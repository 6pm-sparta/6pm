// =====================================================================
// 6pm ticketing-service 직접 기능 검증 (gateway/auth 우회) — ticketing-direct-functional.js
// 목적: gateway/config-server(jwt.secret, hmac.secret-key PAT 이슈)를 거치지 않고
//       ticketing-service(기본 8083)에 X-Id-Card/X-Id-Card-Signature 헤더를 직접 만들어 붙여서
//       "05. 대기열-토큰발급" Postman 시나리오(1~6)를 그대로 재현한다.
// 전제:
//   - ticketing-service 실행 시 VM option:
//       -Dhmac.secret-key=6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long
//   - 로컬 빠른 검증을 위해 큐 스케줄러 주기 단축 권장:
//       -Dqueue.scheduler.delay=2000  (또는 env QUEUE_SCHEDULER_DELAY=2000)
//   - SHOW_ID는 시드된 공연 UUID로 교체
// 실행:  k6 run -e SHOW_ID=<시드된 showId> ticketing-direct-functional.js
// =====================================================================
import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8083'; // ticketing-service 직접(gateway 우회)
const SHOW_ID       = __ENV.SHOW_ID     || 'PUT-SEEDED-SHOW-ID';
const HMAC_SECRET   = __ENV.HMAC_SECRET || '6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long';
const READY_TIMEOUT = parseInt(__ENV.READY_TIMEOUT || '30'); // 초. queue.scheduler.delay 단축 전제
const JSON_HDR      = { 'Content-Type': 'application/json' };

// gateway가 하던 일(JWT 검증 후 UserIdCard 서명)을 k6가 대신 수행.
function idCardHeaders(userId, role = 'MEMBER') {
    const idCardJson = JSON.stringify({ userId, role });
    const signature = crypto.hmac('sha256', HMAC_SECRET, idCardJson, 'base64');
    return {
        headers: {
            ...JSON_HDR,
            'X-Id-Card': idCardJson,
            'X-Id-Card-Signature': signature,
        },
    };
}

export const options = {
    scenarios: {
        functional: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '2m',
        },
    },
    thresholds: {}, // 기능 검증용 — 통과/실패는 check로만 판단
};

export default function () {
    const userA = uuidv4();
    const userB = uuidv4();
    const userC = uuidv4(); // 큐를 아예 안 탄 유저 (구매토큰 없음 케이스)

    const authA = idCardHeaders(userA);
    const authB = idCardHeaders(userB);
    const authC = idCardHeaders(userC);

    // 1. A 대기열 진입
    const enter = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, authA);
    check(enter, { '1. 대기열 진입 200': (r) => r.status === 200 });

    // 2. A 순번 조회 (+ 3. 구매 토큰 발급될 때까지 폴링 — 스케줄러 대기)
    let isReady = false;
    for (let i = 0; i < READY_TIMEOUT; i++) {
        const status = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, authA);
        check(status, { '2. 순번 조회 200': (r) => r.status === 200 });
        const data = status.json('data');
        if (data && data.isReady) { isReady = true; break; }
        sleep(1);
    }
    check(null, { '3. 구매 토큰 발급됨(isReady)': () => isReady });
    if (!isReady) {
        console.error('구매 토큰 발급 안 됨 — queue.scheduler.delay 확인 필요(READY_TIMEOUT 늘리거나 딜레이 단축)');
        return;
    }

    // 좌석목록에서 AVAILABLE 좌석 하나 확보
    const seats = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, authA);
    check(seats, { '좌석목록 200': (r) => r.status === 200 });
    const seatList = seats.json('data') || [];
    const seat = seatList.find((s) => s.status === 'AVAILABLE');
    if (!seat) {
        console.error('AVAILABLE 좌석 없음 — 시드 데이터 확인 필요');
        return;
    }

    // 4. A 좌석 선점 성공 (구매 토큰 보유)
    const holdA = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${seat.seatId}/hold`, null, authA);
    check(holdA, { '4. A 좌석 선점 200': (r) => r.status === 200 });

    // 5. 에러케이스 — 이미 선점된 좌석 재선점 시도(B, 큐 안 탐) → 실패
    const holdB = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${seat.seatId}/hold`, null, authB);
    check(holdB, { '5. B 재선점 시도 4xx': (r) => r.status >= 400 && r.status < 500 });

    // 6. 에러케이스 — 구매 토큰 없는 유저(C)가 다른 좌석 선점 시도 → 실패
    const anotherSeat = seatList.find((s) => s.status === 'AVAILABLE' && s.seatId !== seat.seatId);
    if (anotherSeat) {
        const holdC = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${anotherSeat.seatId}/hold`, null, authC);
        check(holdC, { '6. 구매토큰 없는 유저 4xx': (r) => r.status >= 400 && r.status < 500 });
    }
}
