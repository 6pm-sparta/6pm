// cs-loadtest.js — CS 문의(RAG) 동시성 스모크  [SKELETON]
// LLM(RAG)은 느리고 비싸서 대량 부하 부적합 → "소량 동시성으로 안정성만" 확인.
// ⚠️ cs 담당이 ★ 부분을 실제 값으로 채우세요 (Postman에 없어 추정값임):
//    - CS_PATH: 문의 생성 엔드포인트 (예: /api/v1/cs/inquiries)
//    - 요청 바디 형식
// 실행: k6 run -e VUS=5 cs-loadtest.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";
import { makeTokens } from "./common.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const CS_PATH  = __ENV.CS_PATH || "/api/v1/cs/inquiries";   // ★ 실제 문의 엔드포인트
const VUS      = parseInt(__ENV.VUS || "5");                // LLM이라 소량 (5~10)
const DURATION = __ENV.DURATION || "1m";
const PASSWORD = __ENV.PASSWORD || "Test1234!";

const llmLatency = new Trend("cs_llm_latency", true);  // LLM 응답 지연 별도 추적

export const options = {
    scenarios: { smoke: { executor: "constant-vus", vus: VUS, duration: DURATION } },
    thresholds: { http_req_failed: ["rate<0.05"] },  // LLM은 느리니 p99 임계 대신 실패율만
};

export function setup() {
    return { tokens: makeTokens(BASE_URL, VUS, PASSWORD, "cs") };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const r = http.post(`${BASE_URL}${CS_PATH}`,
        JSON.stringify({ question: "환불은 어떻게 하나요?" }),   // ★ 바디 형식 확인
        { headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, timeout: "60s" });

    llmLatency.add(r.timings.duration);
    check(r, { "문의 2xx": (x) => x.status === 200 || x.status === 201 });
    sleep(1);
}