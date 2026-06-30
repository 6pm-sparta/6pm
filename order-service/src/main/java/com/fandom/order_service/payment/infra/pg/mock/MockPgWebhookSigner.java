package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.infra.pg.HmacCalculator;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Mock PG가 콜백을 보낼 때 X-PG-Signature 헤더값을 만드는 데 사용한다.
 * 실제 PG라면 PG사가 자기 비밀키로 서명하므로 order-service가 직접 서명을 만들 일이 없다 — Mock 전용.
 */
@Component
@RequiredArgsConstructor
public class MockPgWebhookSigner {

    private final OrderProperties orderProperties;
    private final ObjectMapper objectMapper;

    public String sign(PgWebhookRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            byte[] signature = HmacCalculator.hmac(payload, orderProperties.pgWebhook().secretKey());
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("PG 웹훅 서명 생성 실패", e);
        }
    }
}
