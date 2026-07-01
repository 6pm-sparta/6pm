package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * PG 콜백(X-PG-Signature) 서명 검증. 비교는 MessageDigest.isEqual로 한다(타이밍 공격 방지).
 * 검증 대상 payload는 Spring이 바인딩한 PgWebhookRequest를 다시 직렬화한 JSON이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgWebhookHmacUtil {

    private final OrderProperties orderProperties;
    private final ObjectMapper objectMapper;

    public boolean verify(PgWebhookRequest request, String signature) {

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String payload = objectMapper.writeValueAsString(request);
            byte[] expected = HmacCalculator.hmac(payload, orderProperties.pgWebhook().secretKey());
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
}
