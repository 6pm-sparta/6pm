// =====================================================================
// Feed 서비스 부하 테스트 (k6) — feed_timeline_read.js
// 대상: 타임라인 조회 — 일반크리에이터 게시글만 최대 100개 캐싱,
//       대형크리에이터 게시글은 미캐시, 사용자의 대형크리에이터 팔로잉 목록은 10분 캐싱됨.
// 실행: k6 run -e USER_COUNT=50 k6/timeline_read.js
// =====================================================================
import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ── 설정 (env 로 덮어쓰기) ───────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '50');
const PASSWORD = __ENV.PASSWORD || 'Test1234!';
const SMOKE = __ENV.SMOKE === '1';
const TIMELINE_PATH = __ENV.TIMELINE_PATH || '/api/v1/feeds/posts/timeline';
const JSON_HDR = { headers: { 'Content-Type': 'application/json' } };

// ── 부하 시나리오 ────────────────────────────────────────────────────
export const options = {
    scenarios: {
        ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: SMOKE
                ? [{ duration: '20s', target: 5 }]
                : [
                    { duration: '30s', target: 50 },
                    { duration: '1m',  target: 200 },
                    { duration: '1m',  target: 200 }, // 정상상태 유지(측정 구간)
                    { duration: '30s', target: 0 },
                ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: SMOKE ? {} : {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{scenario:ramp}': ['p(99)<500', 'p(95)<300'],
    },
};

// ── setup(): 기존 lt_pool_% 유저로 로그인 → 토큰 풀 생성 ──────────────
// ⚠️ 사전 조건: seed_feed_loadtest_data.sql 을 User DB에서 먼저 실행해야 로그인 가능.
export function setup() {
    const tokens = [];
    for (let i = 1; i <= USER_COUNT; i++) {
        const email = `lt_pool_${i}@loadtest.local`;
        const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email, password: PASSWORD,
        }), JSON_HDR);
        const token = res.json('data.accessToken');
        if (token) tokens.push(token);
    }
    if (tokens.length === 0) {
        throw new Error('토큰 발급 0개 — enable_seed_login.sql 실행 여부 확인');
    }
    console.log(`발급된 토큰: ${tokens.length}/${USER_COUNT}`);
    return { tokens };
}

// ── 가상 유저 1회 행동: 타임라인 조회 ───────────────────────────────────
export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth  = { headers: { Authorization: `Bearer ${token}` } };

    group('timeline_read', () => {
        const r = http.get(`${BASE_URL}${TIMELINE_PATH}`, auth);
        if (r.status !== 200) {
            console.log(`FAIL status=${r.status} body=${r.body}`);
        }
        check(r, {
            '타임라인 조회 200': (x) => x.status === 200,
        });
    });

    sleep(1);
}