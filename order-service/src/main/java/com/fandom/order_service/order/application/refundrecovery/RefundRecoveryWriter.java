package com.fandom.order_service.order.application.refundrecovery;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgTransactionResult;
import com.fandom.order_service.payment.infra.pg.PgTransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 환불 미완료 복구 — 건당 트랜잭션.
 *
 * 처리 순서: 거래조회(PaymentGateway.inquireTransaction)로 PG의 진짜 상태를 먼저 확인한 뒤 분기한다.
 * - REFUNDED: 이미 PG에서는 끝난 건 → 재환불 요청 없이 우리 쪽 상태만 동기화(SYNCED)
 * - REFUND_FAILED/APPROVED: 아직 안 끝난 건 → 재시도 횟수 한도 내면 재환불 요청(RETRIED)
 * - 조회 결과 없음 또는 재시도 소진: 더 이상 자동으로 못 푸는 상태 → MANUAL_REVIEW_REQUIRED(EXHAUSTED)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryWriter {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxAppender outboxAppender;
    private final OrderProperties orderProperties;

    @Transactional
    public RefundRecoveryResult recover(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null
                || (order.getStatus() != OrderStatus.CANCEL_REQUESTED && order.getStatus() != OrderStatus.FAILED)) {
            return RefundRecoveryResult.SKIPPED; // 이미 다른 경로로 상태 변경됨 — 정상 경합
        }

        // 복구 대상 Payment는 REFUND_REQUESTED(환불 대기중) 또는 REFUND_FAILED(거절됨) 상태다.
        Payment payment = paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REFUND_REQUESTED)
                .or(() -> paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REFUND_FAILED))
                .orElse(null);

        if (payment == null || payment.getPgTransactionId() == null) {
            // 환불 대상이 될 결제 기록 자체가 없는 데이터 불일치 — 자동 복구 불가, 즉시 수동 검토.
            log.error("[환불 복구] 환불 대상 Payment를 찾을 수 없습니다. orderId={}", orderId);
            return transitionToManualReview(order, "환불 대상 결제 기록 없음(데이터 불일치)");
        }

        Optional<PgTransactionStatus> inquired = paymentGateway.inquireTransaction(payment.getPgTransactionId());

        if (inquired.isEmpty()) {
            return transitionToManualReview(order, "PG 거래조회 결과 없음");
        }

        PgTransactionResult pgResult = inquired.get().status();

        if (pgResult == PgTransactionResult.REFUNDED) {
            return syncToRefunded(order, payment);
        }

        // REFUND_FAILED/REFUND_REQUESTED — 둘 다 "아직 환불이 안 끝난 상태"이므로 재시도 대상.
        if (payment.hasExhaustedRefundRetries(orderProperties.refundRecovery().maxRetries())) {
            return transitionToManualReview(order, "환불 자동 재시도 소진(" + payment.getRefundRetryCount() + "회)");
        }

        return retryRefund(order, payment);
    }

    private RefundRecoveryResult retryRefund(Order order, Payment payment) {

        // FAILED(환불 거절로 종료됐던 주문)에서 재시도하는 경우, 다시 "환불 처리 대기" 상태로 되돌린다.
        if (order.getStatus() == OrderStatus.FAILED) {
            OrderStatus before = order.getStatus();
            order.markCancelRequested();
            saveHistory(order.getId(), before, order.getStatus(), "[RETRY] 환불 복구 배치: 재시도를 위해 환불 대기로 복귀");
        }

        if (payment.getPaymentStatus() == PaymentStatus.REFUND_FAILED) {
            payment.retryRefundRequest();
        }

        paymentGateway.requestRefundAsync(order.getId(), payment.getPgTransactionId(), payment.getAmount());
        payment.increaseRefundRetryCount();

        log.info("[환불 복구] 재환불 요청. orderId={}, pgTransactionId={}, retryCount={}",
                order.getId(), payment.getPgTransactionId(), payment.getRefundRetryCount());

        return RefundRecoveryResult.RETRIED;
    }

    private RefundRecoveryResult syncToRefunded(Order order, Payment payment) {

        OrderStatus before = order.getStatus();
        order.markCancelCompleted();
        payment.refund();
        saveHistory(order.getId(), before, order.getStatus(), "[RETRY] 환불 복구 배치: 거래조회 결과 동기화(REFUNDED)");

        outboxAppender.appendPaymentCancelled(order.getId());
        outboxAppender.appendOrderCancelledNotification(order.getId(), order.getUserId());

        return RefundRecoveryResult.SYNCED;
    }

    private RefundRecoveryResult transitionToManualReview(Order order, String reason) {

        OrderStatus before = order.getStatus();
        order.markManualReviewRequired();
        saveHistory(order.getId(), before, order.getStatus(), "[RETRY] 환불 복구 배치: " + reason);

        log.error("[환불 복구] 수동 처리 필요. orderId={}, reason={}", order.getId(), reason);

        return RefundRecoveryResult.EXHAUSTED;
    }

    private void saveHistory(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String reason) {
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .orderId(orderId)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .reason(reason)
                        .build());
    }
}
