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
@DisplayName("RefundResultWriter 단위 테스트")
class RefundResultWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private RefundResultWriter refundResultWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refundResultWriter = new RefundResultWriter(orderRepository, orderStatusHistoryRepository, paymentRepository);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order refundRequestedOrder() {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        order.markPaymentRequested();
        order.markPaid();
        order.markRefundRequested();
        return order;
    }

    private Payment approvedPayment() {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey("idem-key")
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("applyRefundSuccess: REFUND_REQUESTED 주문은 REFUNDED로, 결제도 REFUNDED로 전이하고 userId를 반환한다")
    void applyRefundSuccess_transitionsToRefunded() {
        // given
        Order order = refundRequestedOrder();
        Payment payment = approvedPayment();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));

        // when
        Optional<UUID> result = refundResultWriter.applyRefundSuccess(orderId, payment.getId());

        // then
        assertThat(result).contains(userId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualTo(50_000L);
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("applyRefundSuccess: 이미 REFUND_REQUESTED가 아니면(중복 webhook) no-op으로 empty를 반환한다")
    void applyRefundSuccess_alreadyHandled_returnsEmpty() {
        // given
        Order order = refundRequestedOrder();
        order.markRefunded(); // 이미 처리된 상태
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        Optional<UUID> result = refundResultWriter.applyRefundSuccess(orderId, UUID.randomUUID());

        // then
        assertThat(result).isEmpty();
        verify(paymentRepository, never()).findById(any());
        verify(orderStatusHistoryRepository, never()).save(any());
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
        Order order = refundRequestedOrder();
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
    @DisplayName("applyRefundFailure: REFUND_REQUESTED 주문은 FAILED로 전이된다(유저취소/SAGA 보상 경로 공통)")
    void applyRefundFailure_transitionsToFailed() {
        // given
        Order order = refundRequestedOrder();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        boolean applied = refundResultWriter.applyRefundFailure(orderId, "한도 초과");

        // then
        assertThat(applied).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("applyRefundFailure: 이미 REFUND_REQUESTED가 아니면(중복 webhook) no-op으로 false를 반환한다")
    void applyRefundFailure_alreadyHandled_returnsFalse() {
        // given
        Order order = refundRequestedOrder();
        order.markRefunded(); // 이미 처리된 상태
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        boolean applied = refundResultWriter.applyRefundFailure(orderId, "한도 초과");

        // then
        assertThat(applied).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED); // 변경 없음
        verify(orderStatusHistoryRepository, never()).save(any());
    }
}
