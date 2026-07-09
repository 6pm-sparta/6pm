// =====================================================================
// integrated-peak.js — 통합 피크 (여러 시나리오 동시 실행)
// 목적: 각자 "자기 서비스만" 때린 것과 달리, ticketing·feed·login·notification 을
//       "동시에" 걸어 시스템 전체가 피크에서 어떻게 버티는지 관측한다.
//       → 공유자원(gateway·DB풀·Redis·Kafka) 한계 + 연쇄장애(cascade) 규명이 목표.
// 사용: k6/ 폴더에 두고(common.js와 같은 위치), Grafana 대시보드 켠 상태로 실행.
//   로컬:  k6 run -e SHOW_ID=<시드공연UUID> integrated-peak.js
//   운영:  k6 run -e BASE_URL=http://<ALB-DNS> -e SHOW_ID=<AWS시드UUID> integrated-peak.js
//   조절:  -e VU_TICKETING=200 -e VU_FEED=150 -e VU_LOGIN=80 -e VU_NOTI=100
// ⚠️ 실제 엔드포인트 경로는 팀 스크립트(feed_read.js / notification-loadtest.js)에 맞춰 확인할 것.
// =====================================================================
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const SHOW_ID  = __ENV.SHOW_ID  || "";            // 티켓팅 시나리오용(없으면 티켓팅은 스킵)
const PW       = __ENV.PASSWORD || "Test1234!";
const POOL     = parseInt(__ENV.POOL || "50");    // 공용 계정 풀 크기(VU가 라운드로빈 재사용)

// 시나리오별 피크 VU (env로 조절)
const VU_TICKETING = parseInt(__ENV.VU_TICKETING || "150");
const VU_FEED      = parseInt(__ENV.VU_FEED      || "100");
const VU_LOGIN     = parseInt(__ENV.VU_LOGIN     || "50");
const VU_NOTI      = parseInt(__ENV.VU_NOTI      || "80");
const RAMP = __ENV.RAMP || "1m";     // 램프업
const HOLD = __ENV.HOLD || "3m";     // 피크 유지(측정 구간)

const JSON_HDR = { headers: { "Content-Type": "application/json" } };
const errs = new Counter("errors_total");  // k6가 scenario 태그 자동 부착 → 시나리오별 분리 가능

// 4개 시나리오를 startTime:"0s"로 "동시에" 램프업 → 피크가 겹치게
export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        ticketing:    { executor: "ramping-vus", exec: "ticketingFlow", startTime: "0s", startVUs: 0,
            stages: [{ duration: RAMP, target: VU_TICKETING }, { duration: HOLD, target: VU_TICKETING }, { duration: "30s", target: 0 }] },
        feed:         { executor: "ramping-vus", exec: "feedRead",      startTime: "0s", startVUs: 0,
            stages: [{ duration: RAMP, target: VU_FEED },      { duration: HOLD, target: VU_FEED },      { duration: "30s", target: 0 }] },
        login:        { executor: "ramping-vus", exec: "loginFlow",     startTime: "0s", startVUs: 0,
            stages: [{ duration: RAMP, target: VU_LOGIN },     { duration: HOLD, target: VU_LOGIN },     { duration: "30s", target: 0 }] },
        notification: { executor: "ramping-vus", exec: "notiRead",      startTime: "0s", startVUs: 0,
            stages: [{ duration: RAMP, target: VU_NOTI },      { duration: HOLD, target: VU_NOTI },      { duration: "30s", target: 0 }] },
    },
    // 시나리오별 p99 SLO (built-in scenario 태그로 분리). 전체 실패율도 감시.
    thresholds: {
        "http_req_failed": ["rate<0.05"],
        "http_req_duration{scenario:ticketing}":    ["p(99)<1000"],
        "http_req_duration{scenario:feed}":         ["p(99)<1000"],
        "http_req_duration{scenario:login}":        ["p(99)<1500"],
        "http_req_duration{scenario:notification}": ["p(99)<1500"],
    },
};

// setup: 공용 계정 풀 생성(email/pw/token). common.js와 동일하게 stamp로 유니크(409 방지).
export function setup() {
    const users = [];
    const stamp = Date.now();
    for (let i = 0; i < POOL; i++) {
        const email = `peak_${stamp}_${i}@test.com`;
        http.post(`${BASE_URL}/api/v1/members`, JSON.stringify({
            email, password: PW, nickname: `peak${stamp.toString(36)}${i}`,
            zipCode: "12345", address1: "서울", address2: "101",
        }), JSON_HDR);
        const r = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PW }), JSON_HDR);
        const token = r.json("data.accessToken");
        if (token) users.push({ email, token });
    }
    if (!users.length) {
        throw new Error("계정 0개 — gateway/user/auth 기동·Eureka 등록 확인. 재시작 직후면 60초 대기 후 재시도.");
    }
    console.log(`[setup] 공용 계정 ${users.length}/${POOL} 준비 완료`);
    return { users };
}

function pick(u) { return u[(__VU + __ITER) % u.length]; }
function bearer(t) { return { headers: { Authorization: `Bearer ${t}` } }; }

// ── 시나리오 1: 티켓팅 조회 경로 (대기열→순번→좌석목록) ──
export function ticketingFlow(data) {
    if (SHOW_ID) {
        const h = bearer(pick(data.users).token);
        http.post(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue`, null, h);
        http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/queue/status`, h);
        const r = http.get(`${BASE_URL}/api/v1/tickets/shows/${SHOW_ID}/seats`, h);
        if (!check(r, { "ticketing 2xx": (x) => x.status < 400 })) errs.add(1);
    }
    sleep(0.5);
}

// ── 시나리오 2: 피드 조회 ── (경로는 feed_read.js와 맞출 것)
export function feedRead(data) {
    const r = http.get(`${BASE_URL}/api/v1/feeds`, bearer(pick(data.users).token));
    if (!check(r, { "feed 2xx": (x) => x.status < 400 })) errs.add(1);
    sleep(0.5);
}

// ── 시나리오 3: 로그인 (gateway + auth 부하) ──
export function loginFlow(data) {
    const u = pick(data.users);
    const r = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email: u.email, password: PW }), JSON_HDR);
    if (!check(r, { "login 2xx": (x) => x.status < 400 })) errs.add(1);
    sleep(0.5);
}

// ── 시나리오 4: 알림 보관함 조회 ── (경로는 notification-loadtest.js와 맞출 것)
export function notiRead(data) {
    const r = http.get(`${BASE_URL}/api/v1/notifications`, bearer(pick(data.users).token));
    if (!check(r, { "noti 2xx": (x) => x.status < 400 })) errs.add(1);
    sleep(0.5);
}