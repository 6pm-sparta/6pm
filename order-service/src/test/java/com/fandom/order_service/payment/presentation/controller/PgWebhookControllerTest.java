package com.fandom.order_service.payment.presentation.controller;

import com.fandom.common.exception.CustomException;
import com.fandom.common.exception.GlobalExceptionHandler;
import com.fandom.order_service.payment.application.webhook.PgWebhookService;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PgWebhookController 슬라이스 테스트. PaymentControllerTest와 달리 IdCardVerificationFilter를
 * import하지 않는다 — 이 엔드포인트는 X-Id-Card 인증 흐름과 무관하고(서명 기반 검증), 필터도
 * 헤더가 없으면 그냥 통과시키므로 굳이 필요 없다.
 */
@WebMvcTest(PgWebhookController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PgWebhookController 슬라이스 테스트")
class PgWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PgWebhookService pgWebhookService;

    private String body() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "pgTransactionId", "PG-1234",
                "orderId", UUID.randomUUID().toString(),
                "status", "APPROVED",
                "amount", 50_000
        ));
    }

    @Test
    @DisplayName("서명 검증 통과 시 200을 반환한다")
    void receivePaymentWebhook_validSignature_returns200() throws Exception {
        // given
        willDoNothing().given(pgWebhookService).receive(any(), any());

        // when & then
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType("application/json")
                        .content(body())
                        .header("X-PG-Signature", "valid-signature"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("서명 검증 실패 시 401을 반환한다")
    void receivePaymentWebhook_invalidSignature_returns401() throws Exception {
        // given
        willThrow(new CustomException(PaymentErrorCode.INVALID_SIGNATURE))
                .given(pgWebhookService).receive(any(), any());

        // when & then
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType("application/json")
                        .content(body())
                        .header("X-PG-Signature", "invalid-signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("필수값(orderId)이 없으면 400을 반환한다")
    void receivePaymentWebhook_missingRequiredField_returns400() throws Exception {
        // given
        String invalidBody = objectMapper.writeValueAsString(Map.of(
                "pgTransactionId", "PG-1234",
                "status", "APPROVED",
                "amount", 50_000
        ));

        // when & then
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType("application/json")
                        .content(invalidBody)
                        .header("X-PG-Signature", "any-signature"))
                .andExpect(status().isBadRequest());
    }

    // X-PG-Signature 헤더 누락 케이스는 의도적으로 테스트하지 않았다 — GlobalExceptionHandler의
    // catch-all(Exception.class) 핸들러가 MissingRequestHeaderException까지 잡아서 400이 아니라
    // 500으로 응답하는 기존 버그를 발견했다(Idempotency-Key 헤더 케이스도 동일 — PaymentControllerTest의
    // 주석 처리된 missingIdempotencyKey 테스트가 같은 문제를 가리키고 있었던 것으로 보임). common 모듈
    // 전역 동작이라 #108 범위에서 고치지 않고 별도 버그 이슈로 분리해 보고한다.
}
