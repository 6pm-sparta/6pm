// chat-loadtest.js — 채팅 WebSocket(STOMP) 연결/전달 baseline  [SKELETON]
// ⚠️ chat 담당이 ★ 부분을 실제 값으로 채우세요 (Postman에 없어 추정값임):
//    - WS_URL: 채팅 WebSocket 엔드포인트 (예: ws://localhost:8088/ws-stomp)
//    - SUBSCRIBE destination / SEND destination
//    - 멤버십 선결: 채팅방(ROOM_ID) 참여가 되어 있어야 구독/발행 가능
// STOMP over WebSocket, k6/ws 사용.
// 실행: k6 run -e ROOM_ID=<roomId> -e USER_COUNT=50 -e PEAK=50 chat-loadtest.js
import ws from "k6/ws";
import { check } from "k6";
import { makeTokens } from "./common.js";

const SETUP_URL  = __ENV.BASE_URL || "http://localhost:8080";        // 토큰 발급(gateway)
const WS_URL     = __ENV.WS_URL   || "ws://localhost:8088/ws-stomp"; // ★ chat WS 엔드포인트
const ROOM_ID    = __ENV.ROOM_ID  || "PUT-ROOM-ID";                  // ★ 참여한 채팅방 ID
const USER_COUNT = parseInt(__ENV.USER_COUNT || "50");
const PEAK       = parseInt(__ENV.PEAK || "50");
const PASSWORD   = __ENV.PASSWORD || "Test1234!";

export const options = {
    scenarios: { chat: { executor: "ramping-vus", startVUs: 0, stages: [
                { duration: "30s", target: PEAK }, { duration: "1m", target: PEAK }, { duration: "20s", target: 0 },
            ]}},
    thresholds: { ws_connecting: ["p(95)<1000"], checks: ["rate>0.95"] },
};

export function setup() {
    return { tokens: makeTokens(SETUP_URL, USER_COUNT, PASSWORD, "chat") };
}

// STOMP 프레임 생성 헬퍼
function stomp(cmd, headers, body = "") {
    const h = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n");
    return `${cmd}\n${h}\n\n${body}\0`;
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];

    const res = ws.connect(WS_URL, { headers: { Authorization: `Bearer ${token}` } }, (socket) => {
        socket.on("open", () => {
            socket.send(stomp("CONNECT", { "accept-version": "1.2", host: "localhost", Authorization: `Bearer ${token}` }));
        });
        socket.on("message", (msg) => {
            if (msg.startsWith("CONNECTED")) {
                // ★ destination 확인 필요
                socket.send(stomp("SUBSCRIBE", { id: `sub-${__VU}`, destination: `/topic/rooms/${ROOM_ID}` }));
                socket.send(stomp("SEND", { destination: `/app/rooms/${ROOM_ID}/send`, "content-type": "application/json" },
                    JSON.stringify({ content: `hi from VU${__VU}` })));
            }
        });
        socket.on("error", (e) => console.log(`WS error: ${e.error()}`));
        socket.setTimeout(() => socket.close(), 3000);  // 3초 세션 후 종료
    });

    check(res, { "WS 연결(101)": (r) => r && r.status === 101 });
}