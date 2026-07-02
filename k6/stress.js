// =====================================================================
// 6pm 스트레스 부하 — stress.js  (병목 찾기용)
// 핵심: 고VU + "좌석 목록 조회(=DB 읽기)"를 경로에 추가 → HikariCP/DB 압박.
//       기존 큐 경로(Redis)만으론 안 꺾이므로 DB 경로를 섞는다.
// 실행:  k6 run -e SHOW_ID=1 -e USER_COUNT=300 -e PEAK=500 stress.js
//   PEAK 을 500 → 1000 → 1500 으로 올려가며 "꺾이는 지점"을 찾는다.
// =====================================================================
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const SHOW_ID    = __ENV.SHOW_ID    || 'PUT-SEEDED-SHOW-ID';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '300');   // 토큰 풀
const PEAK       = parseInt(__ENV.PEAK || '500');         // 최대 VU (올려가며 한계 탐색)
const SLEEP      = parseFloat(__ENV.SLEEP || '0.3');      // think time(작을수록 압력 ↑)
const PASSWORD   = __ENV.PASSWORD   || 'Test1234!';
const JSON_HDR   = { headers: { 'Content-Type': 'application/json' } };

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: Math.round(PEAK * 0.4) },  // 워밍업 램프
                { duration: '1m',  target: PEAK },                    // 피크까지
                { duration: '2m',  target: PEAK },                    // 피크 유지(여기서 한계 관찰)
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '15s',
        },
    },
    // ※ 깨지는 지점을 보는 게 목적 → 통과 못해도 정상. 기준만 표시.
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{scenario:stress}': ['p(99)<1000'],
    },
};

// 테스트 유저 N명 가입+로그인 → 토큰 풀 1회 생성
export function setup() {
    const tokens = [];
    const stamp = Date.now();
    for (let i = 0; i < USER_COUNT; i++) {
        const email = `stress_${stamp}_${i}@test.com`;
        http.post(`${BASE_URL}/api/v1/members`, JSON.stringify({
            email, password: PASSWORD, nickname: `st${i}`,
            zipCode: '12345', address1: '서울시 강남구', address2: '101호',
        }), JSON_HDR);
        const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email, password: PASSWORD,
        }), JSON_HDR);
        const token = res.json('data.accessToken');
        if (token) tokens.push(token);
    }
    if (tokens.length === 0) throw new Error('토큰 0개 — 가입/로그인 경로 확인');
    console.log(`발급된 토큰: ${tokens.length}/${USER_COUNT} · PEAK=${PEAK} VU`);
    return { tokens };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth  = { headers: { Authorization: `Bearer ${token}` } };

    group('queue_enter', () => {           // Redis 쓰기
        const r = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, auth);
        check(r, { '대기열 200': (x) => x.status === 200 });
    });

    group('queue_status', () => {          // Redis 읽기
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, auth);
        check(r, { '순번 200': (x) => x.status === 200 });
    });

    group('seat_list', () => {             // ★ DB 읽기 — HikariCP/DB 압박 지점
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, auth);
        check(r, { '좌석목록 200': (x) => x.status === 200 });
    });

    sleep(SLEEP);
}