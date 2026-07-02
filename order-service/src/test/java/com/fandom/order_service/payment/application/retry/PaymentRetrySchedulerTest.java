package com.fandom.order_service.payment.application.retry;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRetryScheduler 단위 테스트")
class PaymentRetrySchedulerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentRetryWriter paymentRetryWriter;

    private PaymentRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        OrderProperties.PaymentRetry paymentRetry = new OrderProperties.PaymentRetry(3, 100, 15000L);
        OrderProperties properties = new OrderProperties(null, 0, null, paymentRetry, null, null, null, null, null);
        scheduler = new PaymentRetryScheduler(paymentRepository, paymentRetryWriter, properties);
    }

    private Payment retryablePayment(UUID orderId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("retry-key")
                .build();
        payment.failWithRetry("TRANSIENT:PG 일시적 오류");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("재시도 대상이 없으면 Writer를 호출하지 않는다")
    void retryFailedPayments_noCandidates_doesNothing() {
        // given
        given(paymentRepository.findRetryableOrderIds(any(), any())).willReturn(List.of());

        // when
        scheduler.retryFailedPayments();

        // then
        verify(paymentRetryWriter, never()).prepareRetry(any());
    }

    @Test
    @DisplayName("RETRYING 결과면 requestApproval까지 호출한다")
    void retryFailedPayments_retrying_callsRequestApproval() {
        // given
        UUID orderId = UUID.randomUUID();
        Payment retryPayment = retryablePayment(orderId);
        PaymentRetryResult retryingResult = PaymentRetryResult.retrying(retryPayment);

        given(paymentRepository.findRetryableOrderIds(eq(PaymentStatus.FAILED), any())).willReturn(List.of(orderId));
        given(paymentRetryWriter.prepareRetry(orderId)).willReturn(retryingResult);

        // when
        scheduler.retryFailedPayments();

        // then
        verify(paymentRetryWriter).prepareRetry(orderId);
        verify(paymentRetryWriter).requestApproval(eq(orderId), eq(retryPayment));
    }

    @Test
    @DisplayName("EXHAUSTED 결과면 requestApproval을 호출하지 않는다")
    void retryFailedPayments_exhausted_doesNotCallRequestApproval() {
        // given
        UUID orderId = UUID.randomUUID();
        given(paymentRepository.findRetryableOrderIds(eq(PaymentStatus.FAILED), any())).willReturn(List.of(orderId));
        given(paymentRetryWriter.prepareRetry(orderId)).willReturn(PaymentRetryResult.EXHAUSTED);

        // when
        scheduler.retryFailedPayments();

        // then
        verify(paymentRetryWriter).prepareRetry(orderId);
        verify(paymentRetryWriter, never()).requestApproval(any(), any());
    }

    @Test
    @DisplayName("한 건에서 예외가 발생해도 나머지 건을 계속 처리한다")
    void retryFailedPayments_oneThrows_continuesWithRest() {
        // given
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        Payment retryPayment = retryablePayment(orderId2);

        given(paymentRepository.findRetryableOrderIds(eq(PaymentStatus.FAILED), any())).willReturn(List.of(orderId1, orderId2));
        given(paymentRetryWriter.prepareRetry(orderId1)).willThrow(new RuntimeException("DB 오류"));
        given(paymentRetryWriter.prepareRetry(orderId2)).willReturn(PaymentRetryResult.retrying(retryPayment));

        // when
        scheduler.retryFailedPayments();

        // then — orderId1 실패해도 orderId2는 처리됨
        verify(paymentRetryWriter, times(2)).prepareRetry(any());
        verify(paymentRetryWriter).requestApproval(eq(orderId2), any());
    }
}
