package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PgWebhookHmacUtil;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PgWebhookService 단위 테스트")
class PgWebhookServiceTest {

    @Mock
    private PgWebhookHmacUtil signatureVerifier;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRequestWriter paymentRequestWriter;

    @Mock
    private RefundResultWriter refundResultWriter;

    private final OrderProperties orderProperties = new OrderProperties(
            null, 0, null, null, null,
            new OrderProperties.PgWebhook("secret", "http://localhost", 0L, 600L));

    private PgWebhookService pgWebhookService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pgWebhookService = new PgWebhookService(
                signatureVerifier, redisTemplate, orderProperties, paymentRepository,
                paymentRequestWriter, refundResultWriter);
    }

    private Payment requestedPaymentWithPgTransactionId(String pgTransactionId, Long amount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-key")
                .build();
        payment.recordPgTransactionId(pgTransactionId);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Payment approvedPaymentWithPgTransactionId(String pgTransactionId, Long amount) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId(pgTransactionId)
                .idempotencyKey("idem-key")
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    @Test
    @DisplayName("서명 검증에 실패하면 INVALID_SIGNATURE 예외를 던진다")
    void receive_invalidSignature_throws() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "APPROVED", 50_000L, null);
        given(signatureVerifier.verify(request, "bad-signature")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> pgWebhookService.receive(request, "bad-signature"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_SIGNATURE);

        verify(valueOperations, never()).setIfAbsent(any(), any(), any());
    }

    @Test
    @DisplayName("승인(APPROVED) 콜백을 받으면 applyApproval을 호출하고 결제완료 이벤트를 발행한다")
    void receive_approved_dispatchesApprovalAndPublishesEvent() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "APPROVED", 50_000L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-1234"))
                .willReturn(Optional.of(requestedPaymentWithPgTransactionId("PG-1234", 50_000L)));
        // when
        pgWebhookService.receive(request, "good-signature");

        // then
        verify(paymentRequestWriter).applyApproval(orderId, paymentId);
    }

    @Test
    @DisplayName("실패(FAILED) 콜백을 받으면 applyFailure를 호출하고 결제실패 이벤트를 발행한다")
    void receive_failed_dispatchesFailureAndPublishesEvent() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-5678", orderId, "FAILED", 50_000L, "잔액이 부족합니다.");
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-5678"))
                .willReturn(Optional.of(requestedPaymentWithPgTransactionId("PG-5678", 50_000L)));
        // when
        pgWebhookService.receive(request, "good-signature");

        // then
        verify(paymentRequestWriter).applyFailure(orderId, paymentId, "잔액이 부족합니다.");
    }

    @Test
    @DisplayName("pgTransactionId에 해당하는 결제 건을 찾지 못하면 조용히 무시하고 예외가 없다")
    void receive_unknownPgTransactionId_silentlyIgnored() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-unknown", orderId, "APPROVED", 50_000L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-unknown")).willReturn(Optional.empty());

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
        verify(paymentRequestWriter, never()).applyApproval(any(), any());
    }

    @Test
    @DisplayName("amount가 결제 시도 금액과 다르면 적용하지 않는다")
    void receive_amountMismatch_skipsApplication() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "APPROVED", 999_999L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-1234"))
                .willReturn(Optional.of(requestedPaymentWithPgTransactionId("PG-1234", 50_000L)));

        // when
        pgWebhookService.receive(request, "good-signature");

        // then
        verify(paymentRequestWriter, never()).applyApproval(any(), any());
    }

    @Test
    @DisplayName("이미 처리된 pgTransactionId(중복 수신, Redis dedupe)면 조용히 무시하고 예외가 없다")
    void receive_duplicatePgTransactionId_silentlyIgnored() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "APPROVED", 50_000L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(false); // 이미 누가 선점함

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
        verify(paymentRepository, never()).findByPgTransactionId(any());
    }

    @Test
    @DisplayName("Redis 장애 시 중복수신 1차 방어를 건너뛰고 통과시킨다(예외를 던지지 않음)")
    void receive_redisDown_fallsThroughWithoutError() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "APPROVED", 50_000L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("redis down"));
        given(paymentRepository.findByPgTransactionId("PG-1234")).willReturn(Optional.empty());

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("환불 완료(REFUNDED) 콜백을 받으면 applyRefundSuccess를 호출한다")
    void receive_refunded_dispatchesRefundSuccess() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "REFUNDED", 50_000L, null);
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-1234"))
                .willReturn(Optional.of(approvedPaymentWithPgTransactionId("PG-1234", 50_000L)));

        // when
        pgWebhookService.receive(request, "good-signature");

        // then
        verify(refundResultWriter).applyRefundSuccess(orderId, paymentId);
    }

    @Test
    @DisplayName("환불 거절(REFUND_FAILED) 콜백을 받으면 applyRefundFailure를 호출한다")
    void receive_refundFailed_dispatchesRefundFailure() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", orderId, "REFUND_FAILED", 50_000L, "한도 초과");
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);
        given(paymentRepository.findByPgTransactionId("PG-1234"))
                .willReturn(Optional.of(approvedPaymentWithPgTransactionId("PG-1234", 50_000L)));

        // when
        pgWebhookService.receive(request, "good-signature");

        // then
        verify(refundResultWriter).applyRefundFailure(orderId, "한도 초과");
    }
}
