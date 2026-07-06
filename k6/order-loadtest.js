// order-loadtest.js — SLO-4 결제 (큐 우회: 주문 직접생성 → 결제)
// ⚠️ order-service(8084) 기동 필요. 주문=order 직접(/internal), 결제=gateway 경유.
// 실행: k6 run -e USER_COUNT=100 -e PEAK=80 order-loadtest.js
// ※ 먼저 소량(PEAK=5)으로 "주문생성 2xx, 결제 2xx" 뜨는지 확인 후 본 부하 권장.
import http from "k6/http";
import { check, group, sleep } from "k6";
import { makeUsers, loadOptions, uuid } from "./common.js";

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8080";   // 결제(게이트웨이)
const ORDER_URL  = __ENV.ORDER_URL  || "http://localhost:8084";   // 주문 직접(order-service)
const USER_COUNT = parseInt(__ENV.USER_COUNT || "100");
const PEAK       = parseInt(__ENV.PEAK || "80");
const SLEEP      = parseFloat(__ENV.SLEEP || "0.5");
const PASSWORD   = __ENV.PASSWORD   || "Test1234!";
const JSON_HDR   = { headers: { "Content-Type": "application/json" } };

export const options = loadOptions(PEAK, 1000);   // SLO-4

export function setup() {
    return { users: makeUsers(BASE_URL, USER_COUNT, PASSWORD, "ord") };
}

export default function (data) {
    const u = data.users[__VU % data.users.length];
    let orderId;

    group("order_create", () => {   // order 직접(큐 우회)
        const r = http.post(`${ORDER_URL}/internal/v1/orders`, JSON.stringify({
            holdId: uuid(), seatId: uuid(), userId: u.userId, totalAmount: 50000,
        }), JSON_HDR);
        check(r, { "주문생성 2xx": (x) => x.status === 200 || x.status === 201 });
        orderId = r.json("data.orderId");
    });

    if (orderId) {
        group("payment", () => {      // gateway 경유 결제
            const r = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({
                orderId, paymentMethod: "CARD",
            }), { headers: {
                    Authorization: `Bearer ${u.token}`,
                    "Content-Type": "application/json",
                    "Idempotency-Key": `k6-${__VU}-${__ITER}-${Date.now()}`,   // 매번 유니크
                }});
            check(r, { "결제 2xx": (x) => x.status === 200 || x.status === 201 });
        });
    }
    sleep(SLEEP);
}