// user-loadtest.js — SLO-7 가입/로그인 (인증 경로 자체가 부하 대상)
// ⚠️ user-service(8081) + auth-service(8087) 기동 필요.
// 실행: k6 run -e PEAK=150 user-loadtest.js
// ※ 매 반복마다 유저를 새로 만든다 → DB에 유저가 대량 누적됨. 세션 후 정리(README §정리 방침).
import http from "k6/http";
import { check, group, sleep } from "k6";
import { loadOptions } from "./common.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = "Test1234!";
const PEAK     = parseInt(__ENV.PEAK || "150");
const JSON_HDR = { headers: { "Content-Type": "application/json" } };

export const options = loadOptions(PEAK, 800);   // SLO-7

export default function () {
    const stamp = Date.now();
    const email = `userlt_${stamp}_${__VU}_${__ITER}@test.com`;

    group("signup", () => {
        const r = http.post(`${BASE_URL}/api/v1/members`, JSON.stringify({
            email, password: PASSWORD, nickname: `ul${stamp.toString(36)}${__VU}${__ITER}`,
            zipCode: "12345", address1: "서울", address2: "101",
        }), JSON_HDR);
        check(r, { "가입 2xx": (x) => x.status >= 200 && x.status < 300 });
    });
    group("login", () => {
        const r = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password: PASSWORD }), JSON_HDR);
        check(r, { "로그인 200": (x) => x.status === 200 });
    });
    sleep(0.5);
}