package com.fandom.order_service.payment.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRequestWriter 단위 테스트")
class PaymentRequestWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentRequestWriter paymentRequestWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        paymentRequestWriter = new PaymentRequestWriter(orderRepository, orderStatusHistoryRepository, paymentRepository);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order pendingOrderWithId(Long totalAmount) {
        Order order = Order.createPending(UUID.randomUUID(), userId, totalAmount,
                LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    @Test
    @DisplayName("PENDING 주문이면 PAYMENT_REQUESTED로 전이하고 Payment(REQUESTED)를 DB 금액 기준으로 생성한다")
    void markPaymentRequestedAndSave_pendingOrder_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-1");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_REQUESTED);
        assertThat(result.getAmount()).isEqualTo(50_000L); // 클라이언트 입력이 아니라 order.totalAmount 기준
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(result.getIdempotencyKey()).isEqualTo("idem-key-1");
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("본인 주문이 아니면 PAYMENT_ACCESS_DENIED 예외가 발생한다")
    void markPaymentRequestedAndSave_notOwner_throwsAccessDenied() {
        // given
        Order order = pendingOrderWithId(50_000L);
        UUID strangerId = UUID.randomUUID();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentRequestWriter.markPaymentRequestedAndSave(orderId, strangerId, PaymentMethod.CARD, "idem-key-owner"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("PENDING이 아닌 주문에 결제를 요청하면 INVALID_ORDER_STATUS 예외가 발생한다")
    void markPaymentRequestedAndSave_notPending_throwsInvalidOrderStatus() {
        // given
        Order order = pendingOrderWithId(50_000L);
        order.markPaymentRequested(); // PAYMENT_REQUESTED로 미리 전이시켜 PENDING이 아니게 만듦
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-2"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다")
    void markPaymentRequestedAndSave_orderNotFound_throws() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-3"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("승인 처리 시 Order는 PAID, Payment는 APPROVED + pgTransactionId로 전이된다")
    void applyApproval_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        order.markPaymentRequested();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-key-4")
                .build();
        UUID paymentId = UUID.randomUUID();
        ReflectionTestUtils.setField(payment, "id", paymentId);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyApproval(orderId, paymentId, "PG-1234");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG-1234");

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
    }

    @Test
    @DisplayName("실패 처리 시 Order는 FAILED, Payment는 FAILED + failureReason으로 전이된다")
    void applyFailure_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        order.markPaymentRequested();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-key-5")
                .build();
        UUID paymentId = UUID.randomUUID();
        ReflectionTestUtils.setField(payment, "id", paymentId);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyFailure(orderId, paymentId, "잔액이 부족합니다.");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("잔액이 부족합니다.");
    }
}
