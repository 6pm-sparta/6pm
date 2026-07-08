// =====================================================================
// direct-loadtest.js — 내부 서비스 엔드포인트를 "게이트웨이 우회"로 직접 부하 (범용)
// gateway 천장(~600rps 503)에 막히지 않고 "서비스 자체 한계"를 측정한다.
// idcard.js 와 같은 폴더에 둘 것.
//
// 사용 예:
//   feed 타임라인 직접:
//     k6 run -e TARGET=http://localhost:8082 -e ENDPOINT=/api/v1/feeds/posts/timeline \
//            -e PEAK=800 -e P99=500 --summary-export result_feed_direct.json direct-loadtest.js
//   notification 보관함 직접:
//     k6 run -e TARGET=http://localhost:8085 -e ENDPOINT=/api/v1/notifications -e PEAK=800 direct-loadtest.js
//   ticketing 좌석목록(공개=IdCard 불필요):
//     k6 run -e TARGET=http://localhost:8083 -e ENDPOINT=/api/v1/tickets/shows/<SHOW_ID>/seats \
//            -e IDCARD=false -e PEAK=800 direct-loadtest.js
//   VPC 러너(AWS)에서는 TARGET을 http://<service>.6pm.local:<port> 로, -e HMAC_SECRET_KEY=<AWS값> 추가
// =====================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { idCardHeaders, uuid } from './idcard.js';

const TARGET   = __ENV.TARGET;                          // 예: http://localhost:8082
const ENDPOINT = __ENV.ENDPOINT;                        // 예: /api/v1/feeds/posts/timeline  (PATH는 OS env와 충돌하므로 ENDPOINT 사용)
const METHOD   = (__ENV.METHOD || 'GET').toUpperCase();
const BODY     = __ENV.BODY || null;                    // POST 시 JSON 문자열
const USER_ID  = __ENV.USER_ID || uuid();               // 읽기엔 랜덤 UUID로 충분, 쓰기엔 실제 유저 지정
const ROLE     = __ENV.ROLE || 'MEMBER';
const IDCARD   = (__ENV.IDCARD || 'true') === 'true';   // 공개 엔드포인트면 -e IDCARD=false
const PEAK     = parseInt(__ENV.PEAK || '300');
const P99MS    = parseInt(__ENV.P99 || '500');
const SLEEP    = parseFloat(__ENV.SLEEP || '0.3');

if (!TARGET || !ENDPOINT) {
    throw new Error('TARGET, ENDPOINT 는 필수: -e TARGET=http://host:port -e ENDPOINT=/path');
}

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        load: {
            executor: 'ramping-vus', startVUs: 0,
            stages: [
                { duration: '1m',  target: Math.round(PEAK * 0.4) },
                { duration: '1m',  target: PEAK },
                { duration: '2m',  target: PEAK },   // 측정 구간
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        'http_req_duration{scenario:load}': [`p(99)<${P99MS}`],
    },
};

export default function () {
    const headers = { 'Content-Type': 'application/json' };
    if (IDCARD) Object.assign(headers, idCardHeaders(USER_ID, ROLE));

    const url = `${TARGET}${ENDPOINT}`;
    const res = (METHOD === 'GET')
        ? http.get(url, { headers })
        : http.request(METHOD, url, BODY, { headers });

    check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });

    // 첫 요청이 4xx면 원인(주로 401=시크릿 불일치 / 404=경로오타)을 바로 로그
    if (__VU === 1 && __ITER === 0 && res.status >= 400) {
        console.log(`[첫 요청 실패] status=${res.status} url=${url} body=${String(res.body).slice(0, 300)}`);
    }
    sleep(SLEEP);
}