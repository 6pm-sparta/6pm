// cs-loadtest.js — CS 챗봇(RAG) 소량 동시성 스모크
// ⚠️ 일반 부하 대상이 아니다. LLM(RAG) 응답이라 느리고 비용이 커서
//    "저VU에서 동시 문의가 정상 처리되는지"만 확인한다. (팀 가이드 §8 방침)
// ⚠️ cs-service 기동 + cs.rag.enabled=true + Ollama(또는 Gemini) 준비 필요.
//    Ollama는 keep_alive 기본 5분이라 첫 요청은 콜드스타트로 느릴 수 있음(정상).
// 실행: k6 run -e VUS=5 -e DURATION=1m cs-loadtest.js
import http from "k6/http";
import { check, sleep } from "k6";
import { makeTokens } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const VUS        = parseInt(__ENV.VUS || "5");       // 소량 동시성
const DURATION   = __ENV.DURATION   || "1m";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "10");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";
const P99_MS     = parseInt(__ENV.P99_MS || "20000"); // LLM이라 임계값 크게(20s)

const QUESTIONS = [
    "환불 언제까지 돼요?",
    "예매한 티켓 취소하고 싶어요",
    "좌석은 몇 자리까지 선택 가능한가요?",
    "결제가 실패했는데 어떻게 하나요?",
    "대기열은 어떻게 동작하나요?",
];

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        smoke: { executor: "constant-vus", vus: VUS, duration: DURATION },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"],
        http_req_duration: [`p(99)<${P99_MS}`],
    },
};

export function setup() {
    return { tokens: makeTokens(BASE_URL, USER_COUNT, PASSWORD, "cs") };
}

export default function (data) {
    const auth = { headers: { Authorization: `Bearer ${data.tokens[__VU % data.tokens.length]}`, "Content-Type": "application/json" } };
    const q = QUESTIONS[__ITER % QUESTIONS.length];

    const r = http.post(`${BASE_URL}/api/v1/cs/inquiries`,
        JSON.stringify({ question: q }), auth);
    check(r, {
        "문의 200": (x) => x.status === 200,
        "답변 존재": (x) => (x.json("data.answer") || "").length > 0,
    });

    sleep(1); // LLM 부담 완화용 간격
}
