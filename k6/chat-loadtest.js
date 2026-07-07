// chat-loadtest.js — 실시간 채팅 WebSocket/STOMP 부하 (연결→구독→발행)
// ⚠️ chat-service + gateway + user/auth 기동 필요. 반드시 gateway(8080) 경유해야
//    핸드셰이크에서 IdCard 인증이 주입된다(chat 직접 접속은 인증 없어 거부됨).
//
// 선결 조건(멤버십):
//   STOMP 방 토픽 구독/발행은 "방 멤버"만 가능하다. 테스트 유저가 대상 방의 멤버가
//   되도록 setup에서 CREATOR_ID를 팔로우한다(chat이 user.followed 소비 → 멤버 등록).
//   - CREATOR_ID : 채팅방을 소유한 (시드된) 크리에이터 userId (필수)
//   - ROOM_ID    : 그 크리에이터의 채팅방 id (필수, STOMP 목적지에 사용)
//   팔로우 이벤트가 Kafka로 전파되어 멤버 캐시에 반영될 시간이 필요해 setup에서 대기한다.
//
// 실행: k6 run -e CREATOR_ID=<uuid> -e ROOM_ID=<uuid> -e USER_COUNT=100 -e PEAK=80 chat-loadtest.js
import ws from "k6/ws";
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { makeTokens } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";
const WS_URL     = (__ENV.WS_URL || BASE_URL.replace(/^http/, "ws")) + "/api/v1/chats/ws";
const CREATOR_ID = __ENV.CREATOR_ID || "";
const ROOM_ID    = __ENV.ROOM_ID || "";
const USER_COUNT = parseInt(__ENV.USER_COUNT || "100");
const PEAK       = parseInt(__ENV.PEAK || "80");
const PASSWORD   = __ENV.PASSWORD || "Test1234!";
const SESSION_SEC= parseInt(__ENV.SESSION_SEC || "20");  // 세션 유지 시간
// 메시지 전송 간격(초). 팬은 슬로우모드(기본 5초) 적용 대상.
//  - 기본 6초(>5초): 정상 전달 처리량 측정(대부분 수락)
//  - 5초 미만으로 낮추면: slow 차단 경로(chat_message_blocked_total) 부하 측정
const SEND_EVERY = parseFloat(__ENV.SEND_EVERY || "6");

const NUL = String.fromCharCode(0); // STOMP 프레임 종료자

const connected   = new Counter("stomp_connected");
const msgSent     = new Counter("stomp_msg_sent");
const msgReceived = new Counter("stomp_msg_received");
const connectTime = new Trend("stomp_connect_ms", true);

export const options = {
    scenarios: {
        chat: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: Math.round(PEAK * 0.4) },
                { duration: "1m",  target: PEAK },
                { duration: "1m",  target: PEAK },
                { duration: "30s", target: 0 },
            ],
        },
    },
    thresholds: {
        ws_connecting: ["p(99)<2000"],       // 핸드셰이크 p99 < 2s
        stomp_connected: ["count>0"],         // 최소 한 번은 연결 성공해야
    },
};

export function setup() {
    if (!CREATOR_ID || !ROOM_ID) {
        throw new Error("CREATOR_ID, ROOM_ID 환경변수가 필요합니다. (시드된 크리에이터/채팅방)");
    }
    const tokens = makeTokens(BASE_URL, USER_COUNT, PASSWORD, "chat");
    // 각 유저가 크리에이터를 팔로우 → chat이 user.followed 소비해 방 멤버로 등록
    let followed = 0;
    for (const t of tokens) {
        const r = http.post(`${BASE_URL}/api/v1/follows/${CREATOR_ID}`, null,
            { headers: { Authorization: `Bearer ${t}` } });
        if (r.status >= 200 && r.status < 300) followed++;
    }
    console.log(`[setup] 팔로우 ${followed}/${tokens.length} — Kafka 전파 대기(8s)`);
    sleep(8); // 팔로우 이벤트 전파 + 멤버 캐시 반영 대기
    return { tokens, roomId: ROOM_ID };
}

export default function (data) {
    const token = data.tokens[__VU % data.tokens.length];
    const roomId = data.roomId;
    const params = { headers: { Authorization: `Bearer ${token}` } };
    const start = Date.now();

    const res = ws.connect(WS_URL, params, function (socket) {
        socket.on("open", () => {
            connectTime.add(Date.now() - start);
            // STOMP CONNECT
            socket.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n${NUL}`);
        });

        socket.on("message", (msg) => {
            if (msg.indexOf("CONNECTED") === 0) {
                connected.add(1);
                // 방 토픽 + 개인 큐 구독
                socket.send(`SUBSCRIBE\nid:sub-room\ndestination:/topic/room.${roomId}\n\n${NUL}`);
                socket.send(`SUBSCRIBE\nid:sub-user\ndestination:/user/queue/messages\n\n${NUL}`);
                // 주기적으로 메시지 발행
                socket.setInterval(() => {
                    const body = JSON.stringify({ content: `load ${__VU}-${Date.now()}` });
                    socket.send(`SEND\ndestination:/app/rooms/${roomId}/messages\ncontent-type:application/json\n\n${body}${NUL}`);
                    msgSent.add(1);
                }, SEND_EVERY * 1000);
            } else if (msg.indexOf("MESSAGE") === 0) {
                msgReceived.add(1); // 브로드캐스트/개인 큐 수신
            } else if (msg.indexOf("ERROR") === 0) {
                check(false, { "STOMP ERROR 미발생": () => false });
            }
        });

        socket.setTimeout(() => socket.close(), SESSION_SEC * 1000);
    });

    check(res, { "ws 핸드셰이크 101": (r) => r && r.status === 101 });
}
