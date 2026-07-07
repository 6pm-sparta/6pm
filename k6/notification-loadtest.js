// notification-loadtest.js — 알림 서비스 종합 (토큰 등록/설정 + 보관함 조회)
// ⚠️ notification-service(8085) 기동 필요. gateway/user/auth 도 기동(토큰 발급용).
// 시나리오: 토큰 등록(write) → 설정 조회(read) → 설정 변경(write) → 보관함 조회(read)
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 notification-loadtest.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens, loadOptions, uuid } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "200");
const PEAK       = parseInt(__ENV.PEAK || "150");
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";
const DEVICE_TYPES = ["ANDROID", "IOS", "WEB"];

export const options = loadOptions(PEAK, 800);   // p99 < 800ms

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "noti") };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth = { headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } };

    // 1) 기기 토큰 등록 (write) — deviceToken은 매 반복 유니크(unique 제약)
    let tokenId = null;
    group("token_register", () => {
        const body = JSON.stringify({
            deviceToken: `fcm-${uuid()}`,
            deviceType: DEVICE_TYPES[__ITER % DEVICE_TYPES.length],
        });
        const r = http.post(`${BASE_URL}/api/v1/notifications/tokens`, body, auth);
        check(r, { "토큰등록 200": (x) => x.status === 200 });
        tokenId = r.json("data.id");
    });

    // 2) 설정 조회 (read) + 3) 설정 변경 (write)
    if (tokenId) {
        group("token_setting", () => {
            const g = http.get(`${BASE_URL}/api/v1/notifications/tokens/${tokenId}/settings`, auth);
            check(g, { "설정조회 200": (x) => x.status === 200 });
            const p = http.patch(`${BASE_URL}/api/v1/notifications/tokens/${tokenId}/settings`,
                JSON.stringify({ isNotified: __ITER % 2 === 0 }), auth);
            check(p, { "설정변경 200": (x) => x.status === 200 });
        });
    }

    // 3) 보관함 조회 (read) — 핵심 읽기 경로
    group("notification_inbox", () => {
        const r = http.get(`${BASE_URL}/api/v1/notifications?size=20`, auth);
        check(r, { "보관함조회 200": (x) => x.status === 200 });
    });

    group("notification_list", () => {
        const r = http.get(`${BASE_URL}/api/v1/notifications`, auth);
        check(r, { "알림조회 200": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}