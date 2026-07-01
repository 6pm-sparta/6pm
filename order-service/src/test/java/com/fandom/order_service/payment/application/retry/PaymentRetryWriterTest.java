package com.fandom.order_service.payment.application.retry;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRetryWriter 단위 테스트")
class PaymentRetryWriterTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;

    private PaymentRetryWriter writer;

    private static final int MAX_ATTEMPTS = 3;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        OrderProperties.PaymentRetry paymentRetry = new OrderProperties.PaymentRetry(MAX_ATTEMPTS, 100, 15000L);
        OrderProperties properties = new OrderProperties(null, 0, null, paymentRetry, null, null, null, null, null);
        writer = new PaymentRetryWriter(orderRepository, orderStatusHistoryRepository, paymentRepository, paymentGateway, properties);
        orderId = UUID.randomUUID();
    }

    private Order paymentRequestedOrder() {
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().plusMinutes(10));
        order.markPaymentRequested();
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private UUID latestPaymentId;

    private Payment retryableFailedPayment() {
        latestPaymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-transient")
                .build();
        payment.failWithRetry("TRANSIENT:PG 일시적 오류");
        ReflectionTestUtils.setField(payment, "id", latestPaymentId);
        return payment;
    }

    @Test
    @DisplayName("재시도 조건이 충족되면 새 Payment를 INSERT하고 RETRYING을 반환한다")
    void prepareRetry_eligible_createsNewPaymentAndReturnsRetrying() {
        // given
        Order order = paymentRequestedOrder();
        Payment failedPayment = retryableFailedPayment();

        ReflectionTestUtils.setField(order, "latestPaymentId", latestPaymentId);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(orderId)).willReturn(List.of(failedPayment));
        given(paymentRepository.countByOrderId(orderId)).willReturn(1L); // 1회 시도, MAX_ATTEMPTS(3) 미만
        given(paymentRepository.findById(latestPaymentId)).willReturn(Optional.of(failedPayment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });

        // when
        PaymentRetryResult result = writer.prepareRetry(orderId);

        // then
        assertThat(result.type()).isEqualTo(PaymentRetryResult.Type.RETRYING);
        assertThat(result.retryPayment()).isNotNull();
        assertThat(result.retryPayment().getPaymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(result.retryPayment().getIdempotencyKey()).startsWith("retry-");
        // latest_payment_id 포인터 갱신 확인
        assertThat(order.getLatestPaymentId()).isEqualTo(result.retryPayment().getId());
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("재시도 횟수가 maxAttempts를 초과하면 Order를 FAILED로 전이하고 EXHAUSTED를 반환한다")
    void prepareRetry_exceededMaxAttempts_failsOrderAndReturnsExhausted() {
        // given
        Order order = paymentRequestedOrder();
        Payment failedPayment = retryableFailedPayment();

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(orderId)).willReturn(List.of(failedPayment));
        given(paymentRepository.countByOrderId(orderId)).willReturn((long) MAX_ATTEMPTS + 1); // 초과

        // when
        PaymentRetryResult result = writer.prepareRetry(orderId);

        // then
        assertThat(result.type()).isEqualTo(PaymentRetryResult.Type.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("PAYMENT_REQUESTED가 아닌 주문이면 SKIPPED를 반환한다")
    void prepareRetry_notPaymentRequested_returnsSkipped() {
        // given
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().plusMinutes(10));
        // PENDING 상태 그대로 (PAYMENT_REQUESTED 아님)
        ReflectionTestUtils.setField(order, "id", orderId);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        PaymentRetryResult result = writer.prepareRetry(orderId);

        // then
        assertThat(result.type()).isEqualTo(PaymentRetryResult.Type.SKIPPED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("retryable Payment가 없으면 SKIPPED를 반환한다")
    void prepareRetry_noRetryablePayment_returnsSkipped() {
        // given
        Order order = paymentRequestedOrder();
        Payment permanentFailed = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-perm")
                .build();
        permanentFailed.fail("잔액이 부족합니다."); // retryable=false

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(orderId)).willReturn(List.of(permanentFailed));

        // when
        PaymentRetryResult result = writer.prepareRetry(orderId);

        // then
        assertThat(result.type()).isEqualTo(PaymentRetryResult.Type.SKIPPED);
    }
}
