// =====================================================================
// idcard.js — @CurrentIdCard 서비스를 "게이트웨이 우회"로 직접 부하테스트하기 위한 헬퍼
// (k6 스크립트 폴더에 direct-loadtest.js 와 같이 두고 import)
// 게이트웨이가 하는 것과 동일하게 X-Id-Card(JSON) + X-Id-Card-Signature(HMAC-SHA256 Base64)를 생성한다.
// 근거: common IdCardVerificationFilter / HmacUtils — 받은 JSON 문자열 그대로 HMAC 재계산해 비교.
//
// ⚠️ HMAC_SECRET_KEY 는 "서비스가 쓰는 값과 반드시 동일"해야 함.
//    로컬: 서비스가 기본값을 쓰면 아래 기본값 그대로. .env로 바꿨으면 -e HMAC_SECRET_KEY=... 로 맞추기.
//    AWS : Secrets Manager의 HMAC 값과 동일하게 -e HMAC_SECRET_KEY=... 로 넘길 것.
// =====================================================================
import crypto from 'k6/crypto';

const HMAC_SECRET = __ENV.HMAC_SECRET_KEY
    || '6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long'; // common application.yml 로컬 기본값

// 게이트웨이가 주입하는 두 헤더를 동일 방식으로 생성
export function idCardHeaders(userId, role = 'MEMBER') {
    const payload = JSON.stringify({ userId, role });                          // = X-Id-Card
    const signature = crypto.hmac('sha256', HMAC_SECRET, payload, 'base64');   // = X-Id-Card-Signature
    return { 'X-Id-Card': payload, 'X-Id-Card-Signature': signature };
}

export function uuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}