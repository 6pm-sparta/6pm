// notification-loadtest.js — 알림 조회 (읽기)
// ⚠️ notification-service(8085) 기동 필요.
// 실행: k6 run -e USER_COUNT=200 -e PEAK=150 notification-loadtest.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens, loadOptions } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "200");
const PEAK       = parseInt(__ENV.PEAK || "150");
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";

export const options = loadOptions(PEAK, 800);

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "noti") };
}

export default function (data) {
    const auth = { headers: { Authorization: `Bearer ${data.tokens[__VU % data.tokens.length]}` } };
    group("notification_list", () => {
        const r = http.get(`${BASE_URL}/api/v1/notifications`, auth);
        check(r, { "알림조회 200": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}