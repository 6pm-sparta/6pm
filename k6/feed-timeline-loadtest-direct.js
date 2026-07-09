// =====================================================================
// feed-timeline-loadtest-direct.js — 게이트웨이 우회, feed-service 직접 호출
// X-Id-Card / X-Id-Card-Signature 헤더를 k6에서 직접 생성해서 인증을 통과시킴.
// 목적: 게이트웨이 CircuitBreaker가 진짜 원인인지 격리해서 확인.
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 feed-timeline-loadtest-direct.js
// =====================================================================
import http from "k6/http";
import { check, group, sleep } from "k6";
import crypto from "k6/crypto";
import encoding from "k6/encoding";
import { loadOptions } from "./common.js";

const FEED_BASE_URL      = __ENV.BASE_URL      || "http://localhost:8082";
const AUTH_BASE_URL      = __ENV.BASE_URL      || "http://localhost:8087";
const USER_COUNT    = parseInt(__ENV.USER_COUNT || "200");
const PEAK          = parseInt(__ENV.PEAK || "150");
const SLEEP         = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD      = __ENV.PASSWORD      || "Test1234!";
const TIMELINE_PATH = __ENV.TIMELINE_PATH || "/api/v1/feeds/posts/timeline";
const HMAC_SECRET   = __ENV.HMAC_SECRET || "6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long";
const JSON_HDR       = { headers: { "Content-Type": "application/json" } };

export const options = loadOptions(PEAK, 800);   // SLO-5: p99 < 800ms

// JWT payload 디코딩 — 서명 검증은 하지 않고 userId만 뽑아내는 용도
function decodeJwtPayload(token) {
    const payloadB64Url = token.split('.')[1];
    let b64 = payloadB64Url.replace(/-/g, '+').replace(/_/g, '/');
    while (b64.length % 4) b64 += '=';
    const bytes = new Uint8Array(encoding.b64decode(b64, 'std'));
    const json = String.fromCharCode(...bytes);
    return JSON.parse(json);
}

// X-Id-Card / X-Id-Card-Signature 헤더 생성
// ⚠️ 필드 순서(userId, role)가 UserIdCard 클래스 선언 순서와 정확히 같아야
//   Jackson 기본 직렬화 결과와 바이트 단위로 일치함 (안 맞으면 HMAC 검증 실패)
function buildIdCardHeaders(userId, role) {
    const idCardJson = JSON.stringify({ userId: userId, role: role });
    const signature = crypto.hmac('sha256', HMAC_SECRET, idCardJson, 'base64');
    return {
        'X-Id-Card': idCardJson,
        'X-Id-Card-Signature': signature,
    };
}

// 기존 lt_pool_% 유저로 로그인 → 토큰 배열.
function loginSeedPoolUsers(baseUrl, count, password) {
    const users = [];
    let loggedSample = false;

    for (let i = 1; i <= count; i++) {
        const email = `lt_pool_${i}@loadtest.local`;
        const res = http.post(`${baseUrl}/api/v1/auth/login`,
            JSON.stringify({ email, password }), JSON_HDR);
        const token = res.json("data.accessToken");
        if (!token) continue;

        const claims = decodeJwtPayload(token);
        if (!loggedSample) {
            // ⚠️ 첫 유저의 클레임을 콘솔에 출력 — userId가 실제로 어떤 키에 들어있는지 확인용
            console.log(`[JWT 클레임 샘플] ${JSON.stringify(claims)}`);
            loggedSample = true;
        }

        // 실제 클레임 키로 교체
        const userId = claims.sub;
        const role = claims.role || 'MEMBER';
        if (userId) users.push({ userId, role });
    }

    if (!users.length) {
        throw new Error('로그인된 유저 0명 — seed_feed_loadtest_data.sql 실행 여부 확인');
    }
    console.log(`[setup] 유저 ${users.length}/${count} 준비`);
    return users;
}

export function setup() {
    return { users: loginSeedPoolUsers(AUTH_BASE_URL, USER_COUNT, PASSWORD) };
}

export default function (data) {
    const user = data.users[__VU % data.users.length];
    const headers = buildIdCardHeaders(user.userId, user.role);

    group("feed_timeline_direct", () => {
        const r = http.get(`${FEED_BASE_URL}${TIMELINE_PATH}`, { headers });
        if (r.status !== 200) {
            console.log(`FAIL status=${r.status} body=${r.body}`);
        }
        check(r, { "타임라인 조회 200": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}