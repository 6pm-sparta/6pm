// ticketing-loadtest.js — SLO-1·2 예매 핵심경로 (대기열→순번→좌석목록)
// ⚠️ SHOW_ID는 시드된 공연 UUID. ⚠️ ticketing-service(8083) 기동 필요.
// 실행: k6 run -e SHOW_ID=<uuid> -e USER_COUNT=200 -e PEAK=150 ticketing-loadtest.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens, loadOptions } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const SHOW_ID    = __ENV.SHOW_ID    || "PUT-SEEDED-SHOW-UUID";   // ★ 시드된 공연 UUID로 교체
const USER_COUNT = parseInt(__ENV.USER_COUNT || "200");
const PEAK       = parseInt(__ENV.PEAK || "150");                // 로컬 권장: gateway 벽(~600rps) 아래
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";

export const options = loadOptions(PEAK, 500);   // SLO-2: p99 < 500ms

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "tkt") };
}

export default function (data) {
    const auth = { headers: { Authorization: `Bearer ${data.tokens[__VU % data.tokens.length]}` } };

    group("queue_enter", () => {   // Redis 쓰기
        const r = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, auth);
        check(r, { "대기열 200": (x) => x.status === 200 });
    });
    group("queue_status", () => {  // Redis 읽기
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, auth);
        check(r, { "순번 200": (x) => x.status === 200 });
    });
    group("seat_list", () => {     // DB 읽기 (HikariCP 압박 지점)
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, auth);
        check(r, { "좌석목록 200": (x) => x.status === 200 });
    });
    sleep(SLEEP);
}