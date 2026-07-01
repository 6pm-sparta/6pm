package com.fandom.order_service.payment.infra.pg;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HMAC-SHA256 계산. {@code PgWebhookHmacUtil.verify()}와 mock 패키지의 서명 생성기가 공유한다.
 */
public final class HmacCalculator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacCalculator() {
    }

    public static byte[] hmac(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }
}
