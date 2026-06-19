package com.fandom.order_service.payment.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.config.CommonAuthAutoConfiguration;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.exception.CustomException;
import com.fandom.common.exception.GlobalExceptionHandler;
import com.fandom.order_service.payment.application.PaymentRequestResult;
import com.fandom.order_service.payment.application.PaymentRequestService;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.presentation.dto.request.PaymentRequest;
import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentController 슬라이스 테스트. OrderControllerTest와 동일한 인증 우회 패턴을 사용한다
 * (IdCardVerificationFilter.ID_CARD_ATTRIBUTE를 requestAttr로 직접 주입).
 */
@WebMvcTest(PaymentController.class)
@Import({CommonAuthAutoConfiguration.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "hmac.secret-key=test-secret-key")
@DisplayName("PaymentController 슬라이스 테스트")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentRequestService paymentRequestService;

    private String body(UUID orderId) throws Exception {
        return objectMapper.writeValueAsString(new PaymentRequest(orderId, PaymentMethod.CARD));
    }

    @Test
    @DisplayName("신규 결제 처리 성공 시 201과 결제 정보를 반환한다")
    void requestPayment_newlyProcessed_returns201() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(), orderId, 50_000L, "APPROVED", "CARD", "PG-1234", LocalDateTime.now());

        given(paymentRequestService.requestPayment(any(), eq(userId), eq("idem-key-1")))
                .willReturn(new PaymentRequestResult(response, true));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(orderId))
                        .header("Idempotency-Key", "idem-key-1")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.pgTransactionId").value("PG-1234"));
    }

    @Test
    @DisplayName("멱등 응답(기존 결과 반환) 시 200을 반환한다")
    void requestPayment_idempotentReplay_returns200() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(), orderId, 50_000L, "APPROVED", "CARD", "PG-1234", LocalDateTime.now());

        given(paymentRequestService.requestPayment(any(), eq(userId), eq("idem-key-2")))
                .willReturn(new PaymentRequestResult(response, false));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(orderId))
                        .header("Idempotency-Key", "idem-key-2")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PENDING이 아닌 주문에 결제를 요청하면 409를 반환한다")
    void requestPayment_invalidOrderStatus_returns409() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        given(paymentRequestService.requestPayment(any(), eq(userId), eq("idem-key-3")))
                .willThrow(new CustomException(PaymentErrorCode.INVALID_ORDER_STATUS));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(orderId))
                        .header("Idempotency-Key", "idem-key-3")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("본인 주문이 아니면 403을 반환한다")
    void requestPayment_notOwner_returns403() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        given(paymentRequestService.requestPayment(any(), eq(userId), eq("idem-key-4")))
                .willThrow(new CustomException(PaymentErrorCode.PAYMENT_ACCESS_DENIED));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(orderId))
                        .header("Idempotency-Key", "idem-key-4")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PG 오류 시 502를 반환한다")
    void requestPayment_pgError_returns502() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        given(paymentRequestService.requestPayment(any(), eq(userId), eq("idem-key-5")))
                .willThrow(new CustomException(PaymentErrorCode.PG_ERROR));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(orderId))
                        .header("Idempotency-Key", "idem-key-5")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isBadGateway());
    }

//    @Test
//    @DisplayName("Idempotency-Key 헤더가 없으면 400을 반환한다")
//    void requestPayment_missingIdempotencyKey_returns400() throws Exception {
//        // given
//        UUID userId = UUID.randomUUID();
//        UUID orderId = UUID.randomUUID();
//
//        // when & then
//        mockMvc.perform(post("/api/v1/payments")
//                        .contentType("application/json")
//                        .content(body(orderId))
//                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
//                .andExpect(status().isBadRequest());
//    }

    @Test
    @DisplayName("인증 정보(userIdCard attribute)가 없으면 401을 반환한다")
    void requestPayment_unauthorized_whenNoIdCard() throws Exception {
        // when & then — requestAttr 없이 호출 (인증 정보 없음)
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content(body(UUID.randomUUID()))
                        .header("Idempotency-Key", "idem-key-6"))
                .andExpect(status().isUnauthorized());
    }
}
