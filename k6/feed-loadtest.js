// feed-loadtest.js — SLO-5 피드 타임라인 조회 (읽기·캐시)
// ⚠️ feed-service(8082) 기동 필요. 게시글 시드하면 캐시 효과 관측 더 좋음.
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 feed-loadtest.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens, loadOptions } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "200");
const PEAK       = parseInt(__ENV.PEAK || "150");
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";

export const options = loadOptions(PEAK, 800);   // SLO-5: p99 < 800ms

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "feed") };
}

export default function (data) {
    const auth = { headers: { Authorization: `Bearer ${data.tokens[__VU % data.tokens.length]}` } };
    group("feed_timeline", () => {
        const r = http.get(`${BASE_URL}/api/v1/feeds/posts`, auth);
        check(r, { "타임라인 200": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}