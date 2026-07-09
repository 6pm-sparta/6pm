package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
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
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
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

/**
 * issue #292 — REFUND_REQUESTED→CANCEL_REQUESTED, REFUNDED(order)→CANCELLED 리네이밍.
 * applyRefundFailure에 paymentId 파라미터가 추가돼 이제 payment도 REFUND_FAILED로 같이
 * 전이한다(이전엔 order만 FAILED로 가고 Payment는 APPROVED로 방치되던 버그 수정).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundResultWriter 단위 테스트")
class RefundResultWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    private RefundResultWriter refundResultWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refundResultWriter = new RefundResultWriter(orderRepository, orderStatusHistoryRepository, paymentRepository, outboxAppender);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order cancelRequestedOrder() {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        order.markConfirming();
        order.markCancelRequested();
        return order;
    }

    private Payment refundRequestedPayment() {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey("idem-key")
                .build();
        payment.requestRefund(); // OrderCancelWriter/OrderCompensationWriter가 이미 이 단계까지 전이해둔 상태를 흉내
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("applyRefundSuccess: CANCEL_REQUESTED 주문은 CANCELLED로, 결제는 REFUNDED로 전이하고 환불 완료 알림 이벤트를 Outbox에 적재한다(좌석 해제는 CANCEL_REQUESTED 전이 시점에 이미 발행됨)")
    void applyRefundSuccess_transitionsToCancelled() {
        // given
        Order order = cancelRequestedOrder();
        Payment payment = refundRequestedPayment();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));

        // when
        refundResultWriter.applyRefundSuccess(orderId, payment.getId());

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualTo(50_000L);
        verify(orderStatusHistoryRepository).save(any());
        verify(outboxAppender, never()).appendPaymentCancelled(any());
        verify(outboxAppender).appendOrderCancelledNotification(orderId, userId);
    }

    @Test
    @DisplayName("applyRefundSuccess: 이미 CANCEL_REQUESTED가 아니면(중복 webhook) no-op 처리한다")
    void applyRefundSuccess_alreadyHandled_noOp() {
        // given
        Order order = cancelRequestedOrder();
        order.markCancelCompleted(); // 이미 처리된 상태
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        refundResultWriter.applyRefundSuccess(orderId, UUID.randomUUID());

        // then
        verify(paymentRepository, never()).findById(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxAppender, never()).appendPaymentCancelled(any());
        verify(outboxAppender, never()).appendOrderCancelledNotification(any(), any());
    }

    @Test
    @DisplayName("applyRefundSuccess: 존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다")
    void applyRefundSuccess_orderNotFound_throws() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundResultWriter.applyRefundSuccess(orderId, UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("applyRefundSuccess: pgTransactionId는 맞는데 결제 건을 못 찾으면 PAYMENT_NOT_FOUND 예외가 발생한다 (데이터 불일치 방어)")
    void applyRefundSuccess_paymentNotFound_throws() {
        // given
        Order order = cancelRequestedOrder();
        UUID paymentId = UUID.randomUUID();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundResultWriter.applyRefundSuccess(orderId, paymentId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("applyRefundFailure: CANCEL_REQUESTED 주문은 FAILED로, payment는 REFUND_FAILED로 같이 전이된다(#292 버그 수정)")
    void applyRefundFailure_transitionsToFailedAndPaymentRefundFailed() {
        // given
        Order order = cancelRequestedOrder();
        Payment payment = refundRequestedPayment();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));

        // when
        refundResultWriter.applyRefundFailure(orderId, payment.getId(), "한도 초과");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED); // 이전엔 APPROVED로 방치되던 버그
        assertThat(payment.getFailureReason()).isEqualTo("한도 초과");
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("applyRefundFailure: 이미 CANCEL_REQUESTED가 아니면(중복 webhook) Payment 조회 없이 no-op 처리한다")
    void applyRefundFailure_alreadyHandled_noOp() {
        // given
        Order order = cancelRequestedOrder();
        order.markCancelCompleted(); // 이미 처리된 상태
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        refundResultWriter.applyRefundFailure(orderId, UUID.randomUUID(), "한도 초과");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); // 변경 없음
        verify(paymentRepository, never()).findById(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }
}
