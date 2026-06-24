package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * PG ↔ order-service 콜백(X-PG-Signature) 서명 생성/검증.
 *
 * sign()은 MockPaymentGateway가 콜백을 보낼 때, verify()는 PgWebhookController가 콜백을
 * 받을 때 사용한다 — 항상 대칭.
 * 비교는 MessageDigest.isEqual로 한다(타이밍 공격 방지, 결제 데이터라 더 엄격하게).
 *
 * 검증 대상 payload는 PG가 보낸 raw body가 아니라, Spring이 바인딩한 PgWebhookRequest를
 * ObjectMapper로 "다시 직렬화"한 JSON이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgWebhookHmacUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OrderProperties orderProperties;
    private final ObjectMapper objectMapper;

    /** MockPaymentGateway가 콜백을 보낼 때 X-PG-Signature 헤더값을 만드는 데 사용한다. */
    public String sign(PgWebhookRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            return Base64.getEncoder().encodeToString(hmac(payload));
        } catch (Exception e) {
            throw new IllegalStateException("PG 웹훅 서명 생성 실패", e);
        }
    }

    public boolean verify(PgWebhookRequest request, String signature) {

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payload = objectMapper.writeValueAsString(request);
            byte[] expected = hmac(payload);
            byte[] actual = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual);

        } catch (IllegalArgumentException invalidBase64Signature) {
            log.warn("[PG Webhook] 서명 헤더 형식이 올바르지 않습니다(Base64 디코딩 실패).");
            return false;
        } catch (Exception e) {
            log.error("[PG Webhook] 서명 검증 중 예상치 못한 오류 발생", e);
            return false;
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    orderProperties.pgWebhook().secretKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("PG 웹훅 서명 계산 실패", e);
        }
    }
}
