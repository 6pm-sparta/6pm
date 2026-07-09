// ticketing-overbooking.js — SLO-3 오버부킹 0 (같은 좌석 동시 선점 정합성)
// 목적: 다수 VU가 "같은 좌석"을 동시에 선점 → 오직 1명만 성공해야 함 (오버부킹 0)
//   ※ 처리량이 아니라 "동시성 정확성" 테스트예요.
// ⚠️ 선결:
//   1) 공연 시드(SHOW_ID) + AVAILABLE 좌석 (하나를 SEAT_ID로 지정)
//   2) hold는 큐 통과 '구매 토큰' 보유자만 성공 → ticketing을 QUEUE_SCHEDULER_DELAY=2000 으로 띄워 빠르게 승격
//   3) (서버측 정확 검증은 ticketing_overbooking_total 카운터 — 담당 추가 시 Grafana로도 확인)
// 실행: k6 run -e SHOW_ID=<uuid> -e SEAT_ID=<uuid> -e VUS=50 ticketing-overbooking.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";
import { makeTokens } from "./common.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const SHOW_ID  = __ENV.SHOW_ID  || "PUT-SEEDED-SHOW-UUID";
const SEAT_ID  = __ENV.SEAT_ID  || "PUT-SEEDED-SEAT-UUID";   // 모두가 노릴 "하나의 좌석"
const VUS      = parseInt(__ENV.VUS || "50");
const PASSWORD = __ENV.PASSWORD || "Test1234!";

const holdSuccess  = new Counter("hold_success");   // 200 성공 — 정상이면 총 1
const holdRejected = new Counter("hold_rejected");  // 4xx (이미 선점/토큰없음 등)

export const options = {
    scenarios: {
        // 모든 VU가 "한 번씩" 같은 좌석을 동시 선점 시도
        burst: { executor: "per-vu-iterations", vus: VUS, iterations: 1, maxDuration: "2m" },
    },
};

export function setup() {
    return { tokens: makeTokens(BASE_URL, VUS, PASSWORD, "ob") };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth = { headers: { Authorization: `Bearer ${token}` } };

    // 1) 대기열 진입 → 구매 토큰 확보 (isReady=true 될 때까지 폴링)
    http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, auth);
    let ready = false;
    for (let i = 0; i < 12 && !ready; i++) {
        const s = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, auth);
        ready = s.json("data.isReady") === true;
        if (!ready) sleep(0.5);
    }

    // 2) 같은 좌석 선점 (★ 동시성 지점)
    const r = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${SEAT_ID}/hold`, null, auth);
    if (r.status === 200) holdSuccess.add(1);
    else holdRejected.add(1);
    check(r, { "선점 응답(200 또는 4xx)": (x) => x.status === 200 || (x.status >= 400 && x.status < 500) });
}

// 실행 끝에 오버부킹 판정 출력
export function handleSummary(data) {
    const ok  = data.metrics.hold_success  ? data.metrics.hold_success.values.count  : 0;
    const rej = data.metrics.hold_rejected ? data.metrics.hold_rejected.values.count : 0;
    const verdict = ok === 1 ? "✅ 오버부킹 0 (정합성 정상)" : `⚠️ 성공 ${ok}건 — 1이 아니면 오버부킹 의심`;
    console.log(`\n===== 오버부킹 검증 =====`);
    console.log(`같은 좌석 hold 성공: ${ok} (정상 = 1)`);
    console.log(`hold 거부: ${rej}`);
    console.log(verdict);
    return { stdout: textSummary(data, { indent: " ", enableColors: true }) };
}