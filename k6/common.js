// =====================================================================
// common.js — 부하테스트 공통 헬퍼 (모든 <서비스>-loadtest.js가 재사용)
// 우리가 부하테스트하며 겪은 문제를 여기서 원천 차단한다:
//   - 닉네임/이메일 중복(409)     → 매 실행 stamp로 유니크 생성
//   - p99가 요약에 안 뜸           → summaryTrendStats 표준 지정
//   - setup 실패 원인 모호          → 명확한 에러 메시지
// 사용: import { makeTokens, makeUsers, uuid, loadOptions } from "./common.js";
// ※ 이 파일과 <서비스>-loadtest.js 는 반드시 같은 폴더(k6/)에 둘 것.
// =====================================================================
import http from "k6/http";

const JSON_HDR = { headers: { "Content-Type": "application/json" } };

// v4 UUID 생성 (order의 seatId/holdId 등에 사용)
export function uuid() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === "x" ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

// 표준 부하 옵션 (램프업 + p99 표시 + SLO threshold)
//   peak : 최대 VU  ·  p99ms : SLO p99 기준(ms)
export function loadOptions(peak, p99ms) {
    return {
        summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
        scenarios: {
            load: {
                executor: "ramping-vus",
                startVUs: 0,
                stages: [
                    { duration: "1m",  target: Math.round(peak * 0.4) },
                    { duration: "1m",  target: peak },
                    { duration: "2m",  target: peak },   // 피크 유지(측정 구간)
                    { duration: "30s", target: 0 },
                ],
            },
        },
        thresholds: {
            http_req_failed: ["rate<0.05"],
            ["http_req_duration{scenario:load}"]: [`p(99)<${p99ms}`],
        },
    };
}

// 유저 N명 가입+로그인 → 토큰 배열. 이메일·닉네임 모두 매 실행 유니크(409 방지).
export function makeTokens(baseUrl, count, password, prefix) {
    const tokens = [];
    const stamp = Date.now();
    for (let i = 0; i < count; i++) {
        const email = `${prefix}_${stamp}_${i}@test.com`;
        http.post(`${baseUrl}/api/v1/members`, JSON.stringify({
            email, password, nickname: `${prefix}${stamp.toString(36)}${i}`,
            zipCode: "12345", address1: "서울", address2: "101",
        }), JSON_HDR);
        const res = http.post(`${baseUrl}/api/v1/auth/login`,
            JSON.stringify({ email, password }), JSON_HDR);
        const t = res.json("data.accessToken");
        if (t) tokens.push(t);
    }
    if (!tokens.length) {
        throw new Error("토큰 0개 — gateway/user/auth 기동·Eureka 등록 확인. gateway 재시작 직후면 60초 대기 후 재시도.");
    }
    console.log(`[setup] 토큰 ${tokens.length}/${count} 발급`);
    return tokens;
}

// 토큰 + userId 배열 (order 등 userId가 필요할 때)
export function makeUsers(baseUrl, count, password, prefix) {
    const users = [];
    const stamp = Date.now();
    for (let i = 0; i < count; i++) {
        const email = `${prefix}_${stamp}_${i}@test.com`;
        const s = http.post(`${baseUrl}/api/v1/members`, JSON.stringify({
            email, password, nickname: `${prefix}${stamp.toString(36)}${i}`,
            zipCode: "12345", address1: "서울", address2: "101",
        }), JSON_HDR);
        const userId = s.json("data.userId");
        const res = http.post(`${baseUrl}/api/v1/auth/login`,
            JSON.stringify({ email, password }), JSON_HDR);
        const token = res.json("data.accessToken");
        if (token && userId) users.push({ token, userId });
    }
    if (!users.length) {
        throw new Error("유저 0명 — gateway/user/auth 기동·Eureka 등록 확인.");
    }
    console.log(`[setup] 유저 ${users.length}/${count}`);
    return users;
}