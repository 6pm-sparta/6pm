// =====================================================================
// feed-timeline-loadtest.js — 피드 타임라인 조회 (읽기·캐시)
// ⚠️ feed-service(8082) 기동 필요. 게시글 시드하면 캐시 효과 관측 더 좋음.
// ⚠️ 실제 팔로잉 관계가 있는 기존 시드 유저(lt_pool_%)로 로그인.
//   사전 조건: seed_feed_loadtest_data.sql 을 User DB에서 먼저 실행해야 함.
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 feed-timeline-loadtest.js
// =====================================================================
import http from "k6/http";
import { check, group, sleep } from "k6";
import { loadOptions } from "./common.js";

const BASE_URL      = __ENV.BASE_URL      || "http://localhost:8080";
const USER_COUNT    = parseInt(__ENV.USER_COUNT || "200");
const PEAK          = parseInt(__ENV.PEAK || "150");
const SLEEP         = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD      = __ENV.PASSWORD      || "Test1234!";
const TIMELINE_PATH = __ENV.TIMELINE_PATH || "/api/v1/feeds/posts/timeline";
const JSON_HDR       = { headers: { "Content-Type": "application/json" } };

export const options = loadOptions(PEAK, 800);   // SLO-5: p99 < 800ms

// 기존 lt_pool_% 유저로 로그인 → 토큰 배열.
function loginSeedPoolTokens(baseUrl, count, password) {
    const tokens = [];
    for (let i = 1; i <= count; i++) {
        const email = `lt_pool_${i}@loadtest.local`;
        const res = http.post(`${baseUrl}/api/v1/auth/login`,
            JSON.stringify({ email, password }), JSON_HDR);
        const token = res.json("data.accessToken");
        if (token) tokens.push(token);
    }
    if (!tokens.length) {
        throw new Error("토큰 0개 — seed_feed_loadtest_data.sql 실행 여부 확인. gateway/user/auth 기동·Eureka 등록 확인.");
    }
    console.log(`[setup] 토큰 ${tokens.length}/${count} 발급`);
    return tokens;
}

export function setup() {
    return { tokens: loginSeedPoolTokens(BASE_URL, USER_COUNT, PASSWORD) };
}

export default function (data) {
    const auth = { headers: { Authorization: `Bearer ${data.tokens[__VU % data.tokens.length]}` } };
    group("feed_timeline", () => {
        const r = http.get(`${BASE_URL}${TIMELINE_PATH}`, auth);
        check(r, { "타임라인 조회 150": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}