package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.application.cancellation.OrderCancelDecision;
import com.fandom.order_service.order.application.cancellation.OrderCancelWriter;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelWriter 단위 테스트")
class OrderCancelWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    private OrderCancelWriter orderCancelWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties(
                null, 10, null,
                new OrderProperties.Cancellation(24),
                new OrderProperties.Compensation(3, 1000L), null, null, null);
        orderCancelWriter = new OrderCancelWriter(
                orderRepository, orderStatusHistoryRepository, paymentRepository, orderProperties, outboxAppender);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order orderWithId() {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Payment approvedPaymentFor(UUID orderId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey("idem-key-payment")
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("PENDING 주문은 CANCELLED로 전이되고 PG 호출 없이 즉시 끝난다")
    void decide_pending_cancelsImmediately() {
        // given
        Order order = orderWithId();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderCancelDecision decision = orderCancelWriter.decide(orderId, userId);

        // then
        assertThat(decision.type()).isEqualTo(OrderCancelDecision.Type.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderStatusHistoryRepository).save(any());
        verify(paymentRepository, never()).findByOrderIdAndPaymentStatus(any(), any());
        verify(outboxAppender).appendHoldReleased(order.getId());
    }

    @Test
    @DisplayName("이미 CANCELLED인 주문은 변경 없이 멱등 응답으로 처리된다")
    void decide_alreadyCancelled_returnsIdempotent() {
        // given
        Order order = orderWithId();
        order.markCancelled();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderCancelDecision decision = orderCancelWriter.decide(orderId, userId);

        // then
        assertThat(decision.type()).isEqualTo(OrderCancelDecision.Type.IDEMPOTENT);
        assertThat(decision.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 REFUNDED인 주문도 변경 없이 멱등 응답으로 처리된다")
    void decide_alreadyRefunded_returnsIdempotent() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        order.markPaid();
        order.markRefundRequested();
        order.markRefunded();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderCancelDecision decision = orderCancelWriter.decide(orderId, userId);

        // then
        assertThat(decision.type()).isEqualTo(OrderCancelDecision.Type.IDEMPOTENT);
        assertThat(decision.status()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("PAID 주문은 REFUND_REQUESTED로 전이하고 환불 대상 결제를 함께 반환한다")
    void decide_paid_returnsRefundNeeded() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        order.markPaid();
        Payment payment = approvedPaymentFor(orderId);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));

        // when
        OrderCancelDecision decision = orderCancelWriter.decide(orderId, userId);

        // then
        assertThat(decision.type()).isEqualTo(OrderCancelDecision.Type.REFUND_NEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        assertThat(decision.paymentToRefund()).isEqualTo(payment);
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("CONFIRMED 주문이 취소 가능 시간 내면 REFUND_REQUESTED로 전이된다")
    void decide_confirmedWithinWindow_returnsRefundNeeded() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        order.markPaid();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        ReflectionTestUtils.setField(order, "statusUpdatedAt", LocalDateTime.now().minusHours(1)); // 1시간 전 확정, 윈도우 24h
        Payment payment = approvedPaymentFor(orderId);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));

        // when
        OrderCancelDecision decision = orderCancelWriter.decide(orderId, userId);

        // then
        assertThat(decision.type()).isEqualTo(OrderCancelDecision.Type.REFUND_NEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    @Test
    @DisplayName("CONFIRMED 주문이 취소 가능 시간을 넘기면 CANCELLATION_WINDOW_EXPIRED 예외가 발생한다")
    void decide_confirmedWindowExpired_throws() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        order.markPaid();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        ReflectionTestUtils.setField(order, "statusUpdatedAt", LocalDateTime.now().minusHours(25)); // 윈도우(24h) 초과
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.CANCELLATION_WINDOW_EXPIRED);

        // 윈도우 초과 시 상태는 그대로 CONFIRMED여야 한다 (전이 없음)
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentRepository, never()).findByOrderIdAndPaymentStatus(any(), any());
    }

    @Test
    @DisplayName("취소 불가 상태(PAYMENT_REQUESTED)면 INVALID_ORDER_STATUS 예외가 발생한다")
    void decide_paymentRequested_throwsInvalidOrderStatus() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("취소 불가 상태(COMPENSATING)면 INVALID_ORDER_STATUS 예외가 발생한다")
    void decide_compensating_throwsInvalidOrderStatus() {
        // given
        Order order = orderWithId();
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPENSATING);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("본인 주문이 아니면 ORDER_ACCESS_DENIED 예외가 발생한다")
    void decide_notOwner_throwsAccessDenied() {
        // given
        Order order = orderWithId();
        UUID strangerId = UUID.randomUUID();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, strangerId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다")
    void decide_orderNotFound_throws() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("PAID 취소인데 승인된 결제가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다 (데이터 불일치 방어)")
    void decide_paidWithoutApprovedPayment_throws() {
        // given
        Order order = orderWithId();
        order.markPaymentRequested();
        order.markPaid();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCancelWriter.decide(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }
}
