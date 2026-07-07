// notification-loadtest2.js — 알림 baseline (보관함 조회 + 디바이스 토큰 등록)
// ⚠️ notification-service(8085) 기동 필요.
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 notification-loadtest2.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens, loadOptions } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "200");
const PEAK       = parseInt(__ENV.PEAK || "150");
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";

export const options = loadOptions(PEAK, 800);   // p99 < 800ms

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "noti") };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth = { headers: { Authorization: `Bearer ${token}` } };

    group("notification_list", () => {          // 보관함 조회
        const r = http.get(`${BASE_URL}/api/v1/notifications`, auth);
        check(r, { "알림조회 200": (x) => x.status === 200 });
    });

    group("device_token_register", () => {      // 토큰 등록/설정
        const r = http.post(`${BASE_URL}/api/v1/notifications/tokens`, JSON.stringify({
            deviceToken: `dev-${__VU}-${__ITER}-${Date.now()}`,
            deviceType: "WEB",
        }), { headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } });
        check(r, { "토큰등록 2xx": (x) => x.status === 200 || x.status === 201 });
    });

    sleep(SLEEP);
}