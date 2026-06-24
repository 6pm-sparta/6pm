package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.application.compensation.OrderCompensationResult;
import com.fandom.order_service.order.application.compensation.OrderCompensationWriter;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompensationWriter 단위 테스트")
class OrderCompensationWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private OrderCompensationWriter orderCompensationWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderCompensationWriter = new OrderCompensationWriter(orderRepository, orderStatusHistoryRepository, paymentRepository);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order paidOrder() {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        order.markPaymentRequested();
        order.markPaid();
        return order;
    }

    private Payment approvedPayment() {
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
    @DisplayName("PAID 주문은 COMPENSATING을 거쳐 REFUND_REQUESTED까지 한 트랜잭션에서 전이되고 환불 대상 결제를 함께 반환한다")
    void startCompensation_paid_transitionsToRefundRequested() {
        // given
        Order order = paidOrder();
        Payment payment = approvedPayment();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));

        // when
        OrderCompensationResult result = orderCompensationWriter.startCompensation(orderId, "좌석 매진");

        // then
        assertThat(result.type()).isEqualTo(OrderCompensationResult.Type.REFUND_REQUESTED_STARTED);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.paymentToRefund()).isEqualTo(payment);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        // PAID→COMPENSATING, COMPENSATING→REFUND_REQUESTED 두 단계 모두 이력에 남는다
        verify(orderStatusHistoryRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("이미 COMPENSATING/REFUND_REQUESTED 등으로 처리 중인 주문은 ALREADY_HANDLED로 응답하고 변경하지 않는다")
    void startCompensation_alreadyHandled_isIdempotent() {
        // given
        Order order = paidOrder();
        order.markRefundRequested(); // 유저 직접 취소가 먼저 들어온 상황을 흉내
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderCompensationResult result = orderCompensationWriter.startCompensation(orderId, "좌석 매진");

        // then
        assertThat(result.type()).isEqualTo(OrderCompensationResult.Type.ALREADY_HANDLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED); // 변경 없음
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(paymentRepository, never()).findByOrderIdAndPaymentStatus(any(), any());
    }

    @Test
    @DisplayName("PENDING 상태(도달 불가하지만 방어적 체크)면 SKIPPED_INVALID_STATE로 응답한다")
    void startCompensation_pending_skipsDefensively() {
        // given
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderCompensationResult result = orderCompensationWriter.startCompensation(orderId, "좌석 매진");

        // then
        assertThat(result.type()).isEqualTo(OrderCompensationResult.Type.SKIPPED_INVALID_STATE);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다")
    void startCompensation_orderNotFound_throws() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("PG_ERROR가 아니라 환불 대상 결제가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void startCompensation_noApprovedPayment_throwsPaymentNotFound() {
        // given
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }
}