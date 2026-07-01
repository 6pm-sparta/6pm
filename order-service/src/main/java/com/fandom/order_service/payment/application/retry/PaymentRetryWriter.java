package com.fandom.order_service.payment.application.retry;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 재시도 배치 — 건당 트랜잭션.
 *
 * prepareRetry: 새 Payment INSERT + latest_payment_id 갱신까지 한 트랜잭션.
 * requestApproval: PG 외부 호출이라 트랜잭션 밖에서 실행(PaymentRequestService와 동일한 구조).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentRequestWriter paymentRequestWriter;
    private final OutboxAppender outboxAppender;
    private final OrderProperties orderProperties;

    @Transactional
    public PaymentRetryResult prepareRetry(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PAYMENT_REQUESTED) {
            return PaymentRetryResult.SKIPPED;
        }

        if (order.getLatestPaymentId() == null) {
            log.error("[결제 재시도] latestPaymentId 없음. orderId={}", orderId);
            return PaymentRetryResult.SKIPPED;
        }

        Payment lastPayment = paymentRepository.findById(order.getLatestPaymentId()).orElse(null);

        if (lastPayment == null || !lastPayment.isRetryable()) {
            return PaymentRetryResult.SKIPPED;
        }

        long totalAttempts = paymentRepository.countByOrderId(orderId);
        if (totalAttempts > orderProperties.paymentRetry().maxAttempts()) {
            order.markFailed();
            saveHistory(orderId, OrderStatus.PAYMENT_REQUESTED, OrderStatus.FAILED,
                    "결제 재시도 횟수 초과(" + totalAttempts + "회)");
            outboxAppender.appendPaymentFailed(orderId);
            log.warn("[결제 재시도] 횟수 초과 — FAILED 전이. orderId={}, attempts={}", orderId, totalAttempts);
            return PaymentRetryResult.EXHAUSTED;
        }

        // 새 idempotencyKey로 새 Payment INSERT — 기존 DB UNIQUE 제약 우회
        String newIdempotencyKey = "retry-" + totalAttempts + "-" + orderId;
        Payment retryPayment = Payment.builder()
                .orderId(orderId)
                .amount(order.getTotalAmount())
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(lastPayment.getPaymentMethod())
                .idempotencyKey(newIdempotencyKey)
                .build();

        Payment saved = paymentRepository.save(retryPayment);
        order.updateLatestPayment(saved.getId());

        saveHistory(orderId, OrderStatus.PAYMENT_REQUESTED, OrderStatus.PAYMENT_REQUESTED,
                "결제 재시도 " + totalAttempts + "회차");

        log.info("[결제 재시도] 새 Payment 생성. orderId={}, paymentId={}, attempt={}",
                orderId, saved.getId(), totalAttempts);

        return PaymentRetryResult.retrying(saved);
    }

    /** 트랜잭션 커밋 후 PG 재요청. 실패 시 새 Payment(REQUESTED)는 OrderTimeoutScheduler가 expired_at 기준으로 정리. */
    public void requestApproval(UUID orderId, Payment retryPayment) {
        try {
            String pgTransactionId = paymentGateway.requestApprovalAsync(
                    orderId,
                    retryPayment.getIdempotencyKey(),
                    retryPayment.getAmount(),
                    retryPayment.getPaymentMethod());

            paymentRequestWriter.recordPgTransactionId(retryPayment.getId(), pgTransactionId);

            log.info("[결제 재시도] PG 재요청 접수. orderId={}, pgTransactionId={}", orderId, pgTransactionId);
        } catch (RuntimeException e) {
            // PG 호출 실패 시 새 Payment(REQUESTED)는 OrderTimeoutScheduler가 expired_at 기준으로 정리
            log.error("[결제 재시도] PG 재요청 실패. orderId={}, paymentId={}", orderId, retryPayment.getId(), e);
        }
    }


    private void saveHistory(UUID orderId, OrderStatus from, OrderStatus to, String reason) {
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .orderId(orderId)
                        .fromStatus(from)
                        .toStatus(to)
                        .reason(reason)
                        .build());
    }
}
