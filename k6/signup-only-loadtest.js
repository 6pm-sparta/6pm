import http from "k6/http";
import { check, sleep } from "k6";
import { loadOptions } from "./common.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.PASSWORD || "Test1234!";
const PEAK = parseInt(__ENV.PEAK || "150", 10);
const SLEEP = parseFloat(__ENV.SLEEP || "0.5");
const JSON_HDR = { headers: { "Content-Type": "application/json" } };

export const options = loadOptions(PEAK, 800);

export default function () {
    const stamp = Date.now();
    const runId = stamp.toString(36);
    const email = `signup_${stamp}_${__VU}_${__ITER}@test.com`;

    const response = http.post(`${BASE_URL}/api/v1/members`, JSON.stringify({
        email,
        password: PASSWORD,
        nickname: `su_${runId}_${__VU}_${__ITER}`,
        zipCode: "12345",
        address1: "Seoul",
        address2: "101",
    }), JSON_HDR);

    const ok = check(response, {
        "signup 2xx": (r) => r.status >= 200 && r.status < 300,
    });

    if (!ok) {
        console.log(
            `signup failed status=${response.status}` +
            ` reason=${response.headers["X-Fallback-Reason"] || ""}` +
            ` route=${response.headers["X-Fallback-Route"] || ""}` +
            ` trace=${response.headers["X-Trace-Id"] || ""}` +
            ` body=${response.body ? response.body.slice(0, 300) : ""}`
        );
    }

    sleep(SLEEP);
}
