import http from 'k6/http';
import { check, group, sleep } from 'k6';

const SETUP_BASE_URL = __ENV.SETUP_BASE_URL || 'http://localhost:8080';
const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://localhost:8087';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '200', 10);
const PASSWORD = __ENV.PASSWORD;
const SMOKE = __ENV.SMOKE === '1';
const PEAK = parseInt(__ENV.PEAK || '200', 10);
const SLEEP = parseFloat(__ENV.SLEEP || '1');
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

if (!PASSWORD) {
    throw new Error('PASSWORD env is required. Use -e PASSWORD=<password>.');
}

export const options = {
    scenarios: {
        login: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: SMOKE
                ? [{ duration: '20s', target: 5 }]
                : [
                    { duration: '30s', target: Math.min(50, PEAK) },
                    { duration: '1m', target: PEAK },
                    { duration: '1m', target: PEAK },
                    { duration: '30s', target: 0 },
                ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: SMOKE ? {} : {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{scenario:login}': ['p(95)<300', 'p(99)<800'],
        checks: ['rate>0.99'],
    },
};

export function setup() {
    const users = [];
    const stamp = Date.now();

    for (let i = 0; i < USER_COUNT; i++) {
        const email = `slo7_${stamp}_${i}@test.com`;
        const nickname = `slo7_${stamp}_${i}`;

        const signup = http.post(`${SETUP_BASE_URL}/api/v1/members`, JSON.stringify({
            email,
            password: PASSWORD,
            nickname,
            zipCode: '12345',
            address1: 'Seoul Gangnam-gu',
            address2: '101',
        }), JSON_HEADERS);

        if (signup.status === 201) {
            users.push({ email, password: PASSWORD });
        }
    }

    if (users.length === 0) {
        throw new Error('Prepared 0 users. Check gateway, user-service, and database state.');
    }

    console.log(`Prepared users: ${users.length}/${USER_COUNT}`);
    return { users };
}

export default function (data) {
    const user = data.users[(__VU + __ITER) % data.users.length];

    group('login', () => {
        const response = http.post(`${AUTH_BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email: user.email,
            password: user.password,
        }), JSON_HEADERS);

        check(response, {
            'login status 200': (r) => r.status === 200,
            'access token exists': (r) => !!r.json('data.accessToken'),
        });
    });

    sleep(SLEEP);
}
