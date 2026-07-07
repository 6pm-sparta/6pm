package com.fandom.order_service.payment.application.zombierecovery;

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
@DisplayName("ZombiePaymentRecoveryWriter 단위 테스트")
class ZombiePaymentRecoveryWriterTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private OutboxAppender outboxAppender;

    private ZombiePaymentRecoveryWriter writer;

    private static final String PG_TX_ID = "PG-zombie-1234";

    @BeforeEach
    void setUp() {
        writer = new ZombiePaymentRecoveryWriter(
                orderRepository, paymentRepository, orderStatusHistoryRepository, paymentGateway, outboxAppender);
    }

    // --- 픽스처 헬퍼 ---

    private Order pendingOrder() {
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().minusMinutes(1)); // 이미 만료
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = pendingOrder();
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private Payment requestedPayment(UUID orderId, String pgTransactionId) {
        return Payment.builder()
                .orderId(orderId).amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED).paymentMethod(PaymentMethod.CARD)
                .pgTransactionId(pgTransactionId).idempotencyKey(UUID.randomUUID().toString())
                .build();
    }

    private PgTransactionStatus pgStatus(PgTransactionResult result) {
        return new PgTransactionStatus(PG_TX_ID, UUID.randomUUID(), 50_000L, result, null);
    }

    // --- 정상 분기 ---

    @Test
    @DisplayName("거래조회 결과 APPROVED — 승인 웹훅만 유실된 것이므로 CONFIRMING으로 동기화(APPROVED_SYNCED)")
    void recover_pgApproved_syncsToConfirming() {
        // given
        Order order = pendingOrder();
        UUID orderId = order.getId();
        Payment payment = requestedPayment(orderId, PG_TX_ID);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.APPROVED)));

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.APPROVED_SYNCED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentRepository).clearRetryableFlagByOrderId(orderId);
        verify(outboxAppender).appendPaymentCompleted(orderId);
        verify(outboxAppender, never()).appendPaymentFailed(any());
    }

    @Test
    @DisplayName("거래조회 결과 FAILED — 결제 실패로 정리(FAILED_SYNCED), 좌석/재시도 진행중 플래그 해제")
    void recover_pgFailed_syncsToFailed() {
        // given
        Order order = pendingOrder();
        UUID orderId = order.getId();
        Payment payment = requestedPayment(orderId, PG_TX_ID);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.of(pgStatus(PgTransactionResult.FAILED)));

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.FAILED_SYNCED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outboxAppender).appendPaymentFailed(orderId);
        verify(outboxAppender, never()).appendPaymentCompleted(any());
    }

    @Test
    @DisplayName("거래조회 결과 없음 — FAILED_SYNCED")
    void recover_noTransactionFound_syncsToFailed() {
        // given
        Order order = pendingOrder();
        UUID orderId = order.getId();
        Payment payment = requestedPayment(orderId, PG_TX_ID);

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED))
                .willReturn(Optional.of(payment));
        given(paymentGateway.inquireTransaction(PG_TX_ID)).willReturn(Optional.empty());

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.FAILED_SYNCED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("pgTransactionId 자체가 없는 orphan — 거래조회 호출 없이 바로 FAILED_SYNCED")
    void recover_noPgTransactionId_syncsToFailedWithoutInquiry() {
        // given
        Order order = pendingOrder();
        UUID orderId = order.getId();
        Payment payment = requestedPayment(orderId, null); // PG 호출 실패로 ID 없음

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED))
                .willReturn(Optional.of(payment));

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.FAILED_SYNCED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentGateway, never()).inquireTransaction(any());
    }

    // --- 경합 케이스 ---

    @Test
    @DisplayName("주문이 이미 다른 경로로 처리됨(PENDING 아님) — SKIPPED")
    void recover_orderNotPending_returnsSkipped() {
        // given
        Order order = orderWithStatus(OrderStatus.CONFIRMING); // 정상 webhook 처리됨
        given(orderRepository.findByIdForUpdate(order.getId())).willReturn(Optional.of(order));

        // when
        ZombiePaymentRecoveryResult result = writer.recover(order.getId());

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.SKIPPED);
        verify(paymentRepository, never()).findByOrderIdAndPaymentStatus(any(), any());
    }

    @Test
    @DisplayName("주문 자체가 없는 경우 — SKIPPED")
    void recover_orderNotFound_returnsSkipped() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.SKIPPED);
    }

    @Test
    @DisplayName("REQUESTED Payment가 이미 없음(webhook이 방금 처리함) — SKIPPED")
    void recover_noRequestedPayment_returnsSkipped() {
        // given
        Order order = pendingOrder();
        UUID orderId = order.getId();

        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED))
                .willReturn(Optional.empty());

        // when
        ZombiePaymentRecoveryResult result = writer.recover(orderId);

        // then
        assertThat(result).isEqualTo(ZombiePaymentRecoveryResult.SKIPPED);
        verify(paymentGateway, never()).inquireTransaction(any());
    }
}
