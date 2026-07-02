package com.fandom.order_service.order.application.refundrecovery;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgTransactionResult;
import com.fandom.order_service.payment.infra.pg.PgTransactionStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundRecoveryWriter 단위 테스트")
class RefundRecoveryWriterTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private OutboxAppender outboxAppender;

    private RefundRecoveryWriter writer;

    private static final String PG_TX_ID = "PG-test-1234";
    private static final int MAX_RETRIES = 3;

    @BeforeEach
    void setUp() {
        OrderProperties.RefundRecovery refundRecovery = new OrderProperties.RefundRecovery(100, 10000L, MAX_RETRIES);
        OrderProperties properties = new OrderProperties(null, 0, null, null, null, null, refundRecovery, null);
        writer = new RefundRecoveryWriter(orderRepository, paymentRepository, orderStatusHistoryRepository,
                paymentGateway, outboxAppender, properties);
    }

    // --- 픽스처 헬퍼 ---

    private Order orderWithStatus(OrderStatus status) {
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private Payment approvedPaymentWithRetryCount(UUID orderId, long retryCount) {
        Payment payment = Payment.builder()
                .orderId(orderId).amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED).paymentMethod(PaymentMethod.CARD)
                .pgTransactionId(PG_TX_ID).idempotencyKey(UUID.randomUUID().toString())
                .build();
        ReflectionTestUtils.setField(payment, "refundRetryCount", retryCount);
        return payment;
    }

    private PgTransactionStatus pgStatus(PgTransactionResult result) {
        return new PgTransactionStatus(PG_TX_ID, UUID.randomUUID(), 50_000L, result, null);
    }

    // --- 정상 분기 ---

    @Test
    @DisplayName("거래조회 결과 REFUNDED — 재환불 없이 우리 쪽 상태만 동기화(SYNCED)")
    void recover_pgRefunded_syncsWithoutRetry() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUND_REQUESTED);
        UUID orderId = order.getId();
        Payment payment = approvedPaymentWithRetryCount(orderId, 0);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.REFUNDED)));

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.SYNCED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
        verify(outboxAppender).appendPaymentCancelled(orderId);
        verify(outboxAppender).appendOrderCancelledNotification(any(), any());
    }

    @Test
    @DisplayName("거래조회 결과 REFUND_FAILED, 재시도 횟수 미소진 — 재환불 요청(RETRIED)")
    void recover_pgRefundFailed_retriesWhenNotExhausted() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUND_REQUESTED);
        UUID orderId = order.getId();
        Payment payment = approvedPaymentWithRetryCount(orderId, 1); // 1회 시도, 아직 미소진

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.REFUND_FAILED)));

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.RETRIED);
        assertThat(payment.getRefundRetryCount()).isEqualTo(2L); // increaseRefundRetryCount 호출됨
        verify(paymentGateway).requestRefundAsync(orderId, PG_TX_ID, 50_000L);
        verify(outboxAppender, never()).appendPaymentCancelled(any());
    }

    @Test
    @DisplayName("주문이 FAILED 상태에서 재시도 — REFUND_REQUESTED로 복귀 후 재환불 요청(RETRIED)")
    void recover_failedOrder_transitionsBackToRefundRequestedThenRetries() {
        // given
        Order order = orderWithStatus(OrderStatus.FAILED);
        UUID orderId = order.getId();
        Payment payment = approvedPaymentWithRetryCount(orderId, 0);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.REFUND_FAILED)));

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.RETRIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED); // FAILED → REFUND_REQUESTED 복귀
        verify(paymentGateway).requestRefundAsync(orderId, PG_TX_ID, 50_000L);
    }

    @Test
    @DisplayName("재시도 횟수 소진 — MANUAL_REVIEW_REQUIRED 전환(EXHAUSTED)")
    void recover_retriesExhausted_transitionsToManualReview() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUND_REQUESTED);
        UUID orderId = order.getId();
        Payment payment = approvedPaymentWithRetryCount(orderId, MAX_RETRIES); // 소진

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.REFUND_FAILED)));

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.MANUAL_REVIEW_REQUIRED);
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
    }

    @Test
    @DisplayName("거래조회 결과 없음 — 즉시 MANUAL_REVIEW_REQUIRED 전환(EXHAUSTED)")
    void recover_noTransactionFound_transitionsToManualReview() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUND_REQUESTED);
        UUID orderId = order.getId();
        Payment payment = approvedPaymentWithRetryCount(orderId, 0);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.empty());

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.MANUAL_REVIEW_REQUIRED);
    }

    // --- 경합/예외 케이스 ---

    @Test
    @DisplayName("처리 시점에 이미 다른 경로로 상태 변경 — SKIPPED")
    void recover_alreadyProcessed_returnsSkipped() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUNDED); // 이미 처리됨
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));

        // when
        RefundRecoveryResult result = writer.recover(order.getId());

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.SKIPPED);
        verify(paymentRepository, never()).findByOrderIdAndPaymentStatus(any(), any());
    }

    @Test
    @DisplayName("주문 자체가 없는 경우 — SKIPPED")
    void recover_orderNotFound_returnsSkipped() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.SKIPPED);
    }

    @Test
    @DisplayName("APPROVED Payment가 없는 데이터 불일치 — 즉시 MANUAL_REVIEW_REQUIRED(EXHAUSTED)")
    void recover_noApprovedPayment_transitionsToManualReview() {
        // given
        Order order = orderWithStatus(OrderStatus.REFUND_REQUESTED);
        UUID orderId = order.getId();

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED))
                .willReturn(Optional.empty());

        // when
        RefundRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(RefundRecoveryResult.EXHAUSTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.MANUAL_REVIEW_REQUIRED);
        verify(paymentGateway, never()).inquireTransaction(any());
    }
}
