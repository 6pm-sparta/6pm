// ticketing-scale-loadtest.js — 단계적 스케일업 부하테스트 (조회/대기열조회/선점 비율 혼합)
// 목적: "10만 VU"를 한 번에 걸지 않고 1천 → 10만까지 단계적으로 올리며
//   어느 지점에서 gateway/DB/좌석 정합성이 무너지는지 관측한다.
// ⚠️ 로컬 단일 머신 k6로는 실제 수만 VU를 재현하기 어렵다.
//   - PEAK을 낮춰 실행하거나(예: 5000), k6 Cloud/분산 실행(여러 머신에서 -e PEAK 분담)으로 늘릴 것
//   - 운영 환경에는 절대 바로 걸지 말 것 (스테이징 + 운영급 복제 데이터로 실행)
// ⚠️ 선결:
//   1) 공연 시드(SHOW_ID) + AVAILABLE 좌석 다수 (SEAT_IDS에 콤마로 나열 — 서로 다른 좌석이라
//      "정합성"이 아니라 "처리량"을 재는 테스트가 된다. 하나만 넣으면 hold가 대부분 4xx로 막혀 스루풋을 왜곡함)
//   2) hold는 대기열 통과 '구매 토큰' 보유자만 성공 → ticketing을 QUEUE_SCHEDULER_DELAY=2000 등으로 짧게 띄워 승격 빠르게
// 실행 예:
//   k6 run -e SHOW_ID=<uuid> -e SEAT_IDS=<uuid1>,<uuid2>,... -e PEAK=1000 ticketing-scale-loadtest.js
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeTokens } from "./common.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

const BASE_URL  = __ENV.BASE_URL  || "http://localhost:8080";
const SHOW_ID   = __ENV.SHOW_ID   || "PUT-SEEDED-SHOW-UUID";
const SEAT_IDS  = (__ENV.SEAT_IDS || "PUT-SEEDED-SEAT-UUID-1,PUT-SEEDED-SEAT-UUID-2").split(",");
const USER_COUNT = parseInt(__ENV.USER_COUNT || "500");
const PEAK       = parseInt(__ENV.PEAK || "1000"); // 목표 최대 VU (10만까지 올리고 싶으면 분산 실행 필요)
const PASSWORD   = __ENV.PASSWORD || "Test1234!";

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        ramping_users: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "2m",  target: Math.round(PEAK * 0.01) }, // 1차: 1%
                { duration: "3m",  target: Math.round(PEAK * 0.1)  }, // 2차: 10%
                { duration: "5m",  target: Math.round(PEAK * 0.5)  }, // 3차: 50%
                { duration: "5m",  target: PEAK },                    // 최대 부하 (측정 구간)
                { duration: "2m",  target: 0 },                       // 부하 감소
            ],
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"],
        "http_req_duration{group:::좌석조회}": ["p(95)<500"],
        "http_req_duration{group:::대기열조회}": ["p(95)<500"],
        "http_req_duration{group:::좌석선점}": ["p(95)<800"],
    },
};

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "scale") };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const auth = { headers: { Authorization: `Bearer ${token}` } };

    // 실제 사용자 행동 비율: 좌석 조회 80% / 대기열 상태 확인 15% / 선점 시도 5%
    const roll = Math.random();

    if (roll < 0.8) {
        group("좌석조회", () => {
            const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, auth);
            check(r, { "좌석 목록 200": (x) => x.status === 200 });
        });
    } else if (roll < 0.95) {
        group("대기열조회", () => {
            const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, auth);
            check(r, { "대기열 상태 200": (x) => x.status === 200 });
        });
    } else {
        group("좌석선점", () => {
            const seatId = SEAT_IDS[Math.floor(Math.random() * SEAT_IDS.length)];
            const r = http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats/${seatId}/hold`, null, auth);
            check(r, { "선점 응답(200 또는 4xx)": (x) => x.status === 200 || (x.status >= 400 && x.status < 500) });
        });
    }

    sleep(1);
}

export function handleSummary(data) {
    return { stdout: textSummary(data, { indent: " ", enableColors: true }) };
}
