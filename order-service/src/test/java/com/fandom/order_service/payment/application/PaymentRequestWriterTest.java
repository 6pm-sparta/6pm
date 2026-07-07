package com.fandom.order_service.payment.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
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

/**
 * issue #292 — orders.status에서 PAYMENT_REQUESTED가 빠지면서 PaymentRequestWriter의 가드가
 * order.status 단일 체크에서 payment.paymentStatus(1차) + order.status(2차) 2단계로 바뀌었다.
 * 관련 테스트를 전면 재작성했다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRequestWriter 단위 테스트")
class PaymentRequestWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    private PaymentRequestWriter paymentRequestWriter;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        paymentRequestWriter = new PaymentRequestWriter(orderRepository, orderStatusHistoryRepository, paymentRepository, outboxAppender);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order pendingOrderWithId(Long totalAmount) {
        Order order = Order.createPending(UUID.randomUUID(), userId, totalAmount,
                LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Payment requestedPayment(UUID paymentId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-" + paymentId)
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    @Test
    @DisplayName("PENDING 주문이고 진행중 결제가 없으면 Payment(REQUESTED)를 DB 금액 기준으로 생성한다(order.status는 PENDING 유지)")
    void markPaymentRequestedAndSave_pendingOrder_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)).willReturn(false);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-1");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING); // issue #292 — 더 이상 PAYMENT_REQUESTED로 전이하지 않음
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
        order.markConfirming(); // PENDING이 아니게 만듦
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-2"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("issue #292 — PENDING이어도 이미 진행중(REQUESTED) 결제가 있으면 INVALID_ORDER_STATUS 예외가 발생한다(동시 결제 요청 뮤텍스)")
    void markPaymentRequestedAndSave_alreadyRequested_throwsInvalidOrderStatus() {
        // given
        Order order = pendingOrderWithId(50_000L);
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> paymentRequestWriter.markPaymentRequestedAndSave(orderId, userId, PaymentMethod.CARD, "idem-key-dup"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);
        verify(paymentRepository, never()).save(any());
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
    @DisplayName("승인 처리 시 Order는 CONFIRMING, Payment는 APPROVED로 전이되고 결제완료 이벤트를 Outbox에 적재한다")
    void applyApproval_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);
        payment.recordPgTransactionId("PG-1234"); // 요청 접수 시점에 이미 기록돼 있는 상태를 흉내냄

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        paymentRequestWriter.applyApproval(orderId, paymentId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG-1234"); // approve()는 더 이상 이 값을 건드리지 않음
        verify(orderStatusHistoryRepository).save(any());
        verify(outboxAppender).appendPaymentCompleted(orderId);
        verify(paymentRepository).clearRetryableFlagByOrderId(orderId); // retryable 플래그 정리 확인
    }

    @Test
    @DisplayName("Payment가 이미 REQUESTED가 아니면(중복 redelivery) Order 조회 없이 1차 가드에서 no-op 처리한다")
    void applyApproval_paymentAlreadyProcessed_noOp() {
        // given
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);
        payment.approve(); // 첫 콜백으로 이미 처리된 상태

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyApproval(orderId, paymentId);

        // then — 1차 가드(Payment 기준)에서 바로 리턴되므로 Order는 조회조차 하지 않는다
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxAppender, never()).appendPaymentCompleted(any());
    }

    @Test
    @DisplayName("Payment는 REQUESTED인데 Order가 이미 다른 경로로 종결된 경우(레이스) 2차 가드에서 no-op하고 Order를 변경하지 않는다")
    void applyApproval_orderAlreadySettled_noOp() {
        // given
        Order order = pendingOrderWithId(50_000L);
        order.markCancelled(); // 유저 취소/타임아웃 등으로 이미 종결됨
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        paymentRequestWriter.applyApproval(orderId, paymentId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); // 변경 없음
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REQUESTED); // 변경 없음
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxAppender, never()).appendPaymentCompleted(any());
    }

    @Test
    @DisplayName("실패 처리 시 Order는 FAILED, Payment는 FAILED + failureReason으로 전이되고 결제실패 이벤트를 Outbox에 적재한다")
    void applyFailure_success() {
        // given
        Order order = pendingOrderWithId(50_000L);
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        paymentRequestWriter.applyFailure(orderId, paymentId, "잔액이 부족합니다.");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("잔액이 부족합니다.");
        verify(outboxAppender).appendPaymentFailed(orderId);
    }

    @Test
    @DisplayName("Payment가 이미 REQUESTED가 아니면(중복 redelivery) Order 조회 없이 1차 가드에서 no-op 처리한다")
    void applyFailure_paymentAlreadyProcessed_noOp() {
        // given
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);
        payment.fail("이미 처리됨");

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyFailure(orderId, paymentId, "잔액이 부족합니다.");

        // then
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxAppender, never()).appendPaymentFailed(any());
    }

    @Test
    @DisplayName("일시적 오류 처리 시 Payment만 FAILED + retryable=true로 마킹되고 Order는 건드리지 않는다")
    void applyFailureWithRetry_success() {
        // given
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyFailureWithRetry(orderId, paymentId, "TRANSIENT:PG 일시적 오류");

        // then — issue #292: orders.status는 결제 시도 동안 건드리지 않으므로 Order 조회 자체가 없다
        verify(orderRepository, never()).findByIdForUpdate(any());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.isRetryable()).isTrue();
        assertThat(payment.getFailureReason()).isEqualTo("TRANSIENT:PG 일시적 오류");
        verify(orderStatusHistoryRepository).save(any());
        // appendPaymentFailed는 호출되지 않는다 — 재시도 여지가 있으므로
        verify(outboxAppender, never()).appendPaymentFailed(any());
    }

    @Test
    @DisplayName("일시적 오류 처리 시 Payment가 이미 REQUESTED가 아니면 no-op 처리한다")
    void applyFailureWithRetry_paymentAlreadyProcessed_noOp() {
        // given
        UUID paymentId = UUID.randomUUID();
        Payment payment = requestedPayment(paymentId);
        payment.approve(); // 이미 다른 경로로 처리된 상태

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when
        paymentRequestWriter.applyFailureWithRetry(orderId, paymentId, "TRANSIENT:PG 일시적 오류");

        // then
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED); // 변경 없음
    }
}
