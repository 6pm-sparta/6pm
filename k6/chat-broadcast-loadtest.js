// chat-broadcast-loadtest.js — 크리에이터 브로드캐스트 fan-out 부하
// chat의 가장 무거운 경로: 크리에이터 1명이 메시지를 보내면 방 토픽으로 온라인 팬 N명에게 fan-out.
// (chat-loadtest.js 는 팬→크리에이터 개인전달만 재므로, 이 파일이 브로드캐스트 fan-out을 담당)
//
// 구조(k6 멀티 시나리오):
//   - fans     : 팬 N명이 접속 + /topic/room.{ROOM_ID} 구독 후 수신만(브로드캐스트 받는 쪽)
//   - creator  : 크리에이터 1명이 접속 후 주기적으로 발행(브로드캐스트 유발). 팬 구독 뒤 시작(startTime)
//
// ⚠️ 선결:
//   - 반드시 gateway(8080) 경유(핸드셰이크 IdCard 인증)
//   - CREATOR_ID: 시드된 크리에이터 userId (팬들이 팔로우해 방 멤버가 됨)
//   - ROOM_ID   : 그 크리에이터의 채팅방 id
//   - CREATOR_TOKEN: 크리에이터 계정의 JWT (크리에이터로 발행해야 SenderRole=CREATOR → 브로드캐스트).
//                    크리에이터 계정으로 로그인해 accessToken을 넣는다.
//
// 실행: k6 run -e CREATOR_ID=<uuid> -e ROOM_ID=<uuid> -e CREATOR_TOKEN=<jwt> \
//              -e FAN_COUNT=200 -e PEAK=150 chat-broadcast-loadtest.js
import ws from "k6/ws";
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { makeTokens } from "./common.js";

const BASE_URL      = __ENV.BASE_URL   || "http://localhost:8080";
const WS_URL        = (__ENV.WS_URL || BASE_URL.replace(/^http/, "ws")) + "/api/v1/chats/ws";
const CREATOR_ID    = __ENV.CREATOR_ID || "";
const ROOM_ID       = __ENV.ROOM_ID || "";
const CREATOR_TOKEN = __ENV.CREATOR_TOKEN || "";
const FAN_COUNT     = parseInt(__ENV.FAN_COUNT || "200");
const PEAK          = parseInt(__ENV.PEAK || "150");
const PASSWORD      = __ENV.PASSWORD || "Test1234!";
const SESSION_SEC   = parseInt(__ENV.SESSION_SEC || "90");   // 팬 구독 유지 시간(브로드캐스트 받는 창)
const BROADCAST_EVERY = parseFloat(__ENV.BROADCAST_EVERY || "1"); // 크리에이터 발행 간격(초). 크리에이터는 slow 면제

const NUL = String.fromCharCode(0);

const connected     = new Counter("stomp_connected");
const bcastSent     = new Counter("stomp_broadcast_sent");     // 크리에이터가 발행한 수
const bcastReceived = new Counter("stomp_broadcast_received"); // 팬들이 받은 총 수(fan-out 총량)
const connectTime   = new Trend("stomp_connect_ms", true);

export const options = {
    scenarios: {
        // 팬: 먼저 램프업하여 구독
        fans: {
            executor: "ramping-vus",
            exec: "fanSubscriber",
            startVUs: 0,
            stages: [
                { duration: "20s", target: Math.round(PEAK * 0.5) },
                { duration: "20s", target: PEAK },
                { duration: "60s", target: PEAK },
                { duration: "10s", target: 0 },
            ],
        },
        // 크리에이터: 팬들이 구독을 마칠 시간을 준 뒤(25s) 발행 시작
        creator: {
            executor: "constant-vus",
            exec: "creatorPublisher",
            vus: 1,
            startTime: "25s",
            duration: "60s",
        },
    },
    thresholds: {
        ws_connecting: ["p(99)<2000"],
        stomp_connected: ["count>0"],
        stomp_broadcast_received: ["count>0"], // 팬이 실제로 브로드캐스트를 받아야
    },
};

export function setup() {
    if (!CREATOR_ID || !ROOM_ID || !CREATOR_TOKEN) {
        throw new Error("CREATOR_ID, ROOM_ID, CREATOR_TOKEN 환경변수가 필요합니다.");
    }
    const fanTokens = makeTokens(BASE_URL, FAN_COUNT, PASSWORD, "bcfan");
    let followed = 0;
    for (const t of fanTokens) {
        const r = http.post(`${BASE_URL}/api/v1/follows/${CREATOR_ID}`, null,
            { headers: { Authorization: `Bearer ${t}` } });
        if (r.status >= 200 && r.status < 300) followed++;
    }
    console.log(`[setup] 팬 팔로우 ${followed}/${fanTokens.length} — Kafka 전파 대기(8s)`);
    sleep(8);
    return { fanTokens, roomId: ROOM_ID };
}

// 팬: 접속 → 방 토픽 구독 → 브로드캐스트 수신만
export function fanSubscriber(data) {
    const token = data.fanTokens[__VU % data.fanTokens.length];
    const start = Date.now();
    const res = ws.connect(WS_URL, { headers: { Authorization: `Bearer ${token}` } }, function (socket) {
        socket.on("open", () => {
            connectTime.add(Date.now() - start);
            socket.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n${NUL}`);
        });
        socket.on("message", (msg) => {
            if (msg.indexOf("CONNECTED") === 0) {
                connected.add(1);
                socket.send(`SUBSCRIBE\nid:sub-room\ndestination:/topic/room.${data.roomId}\n\n${NUL}`);
            } else if (msg.indexOf("MESSAGE") === 0) {
                bcastReceived.add(1);
            }
        });
        socket.setTimeout(() => socket.close(), SESSION_SEC * 1000);
    });
    check(res, { "ws 핸드셰이크 101": (r) => r && r.status === 101 });
}

// 크리에이터: 접속 → 주기적으로 발행(브로드캐스트 유발). 크리에이터는 slow/dup 면제
export function creatorPublisher(data) {
    const res = ws.connect(WS_URL, { headers: { Authorization: `Bearer ${CREATOR_TOKEN}` } }, function (socket) {
        socket.on("open", () => {
            socket.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n${NUL}`);
        });
        socket.on("message", (msg) => {
            if (msg.indexOf("CONNECTED") === 0) {
                connected.add(1);
                socket.setInterval(() => {
                    const body = JSON.stringify({ content: `broadcast ${Date.now()}` });
                    socket.send(`SEND\ndestination:/app/rooms/${data.roomId}/messages\ncontent-type:application/json\n\n${body}${NUL}`);
                    bcastSent.add(1);
                }, BROADCAST_EVERY * 1000);
            } else if (msg.indexOf("ERROR") === 0) {
                check(false, { "크리에이터 STOMP ERROR 미발생": () => false });
            }
        });
        // 발행 구간(scenario duration)만큼 유지
        socket.setTimeout(() => socket.close(), 60 * 1000);
    });
    check(res, { "크리에이터 ws 핸드셰이크 101": (r) => r && r.status === 101 });
}
