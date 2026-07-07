package com.fandom.order_service.payment.application.zombierecovery;

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
 * 좀비 결제 정리 — 건당 트랜잭션. OrderTimeoutWriter는 REQUESTED 결제가 있는 만료 주문을
 * 진행중 플래그 유지를 위해 건너뛰는데, webhook 유실 시 이 상태가 영구화된다. PG 거래조회로 실제
 * 상태를 확인해 정리한다: APPROVED → CONFIRMING 동기화, 그 외 → FAILED 동기화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZombiePaymentRecoveryWriter {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxAppender outboxAppender;

    @Transactional
    public ZombiePaymentRecoveryResult recover(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return ZombiePaymentRecoveryResult.SKIPPED; // 정상 경합
        }

        Payment payment = paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)
                .orElse(null);

        if (payment == null) {
            return ZombiePaymentRecoveryResult.SKIPPED; // 정상 경합
        }

        if (payment.getPgTransactionId() == null) {
            // PG 호출 자체 실패로 거래 ID 없음 — 조회 불가
            return syncToFailed(order, payment, "[좀비 정리] PG 요청 접수 기록 없음(PG 호출 자체 실패 추정)");
        }

        Optional<PgTransactionStatus> inquired = paymentGateway.inquireTransaction(payment.getPgTransactionId());

        if (inquired.isEmpty()) {
            return syncToFailed(order, payment, "[좀비 정리] PG 거래조회 결과 없음");
        }

        if (inquired.get().status() == PgTransactionResult.APPROVED) {
            return syncToConfirming(order, payment);
        }

        String reason = inquired.get().failureReason() != null
                ? inquired.get().failureReason()
                : "[좀비 정리] PG 거래조회 결과 실패(APPROVED 아님)";
        return syncToFailed(order, payment, reason);
    }

    private ZombiePaymentRecoveryResult syncToConfirming(Order order, Payment payment) {

        OrderStatus before = order.getStatus();
        order.markConfirming();
        payment.approve();
        paymentRepository.clearRetryableFlagByOrderId(order.getId());
        saveHistory(order.getId(), before, order.getStatus(), "[RETRY] 좀비 결제 정리: 거래조회 결과 동기화(APPROVED)");

        outboxAppender.appendPaymentCompleted(order.getId());

        log.info("[좀비 결제 정리] 승인 동기화. orderId={}, paymentId={}", order.getId(), payment.getId());
        return ZombiePaymentRecoveryResult.APPROVED_SYNCED;
    }

    private ZombiePaymentRecoveryResult syncToFailed(Order order, Payment payment, String reason) {

        OrderStatus before = order.getStatus();
        order.markFailed();
        payment.fail(reason);
        saveHistory(order.getId(), before, order.getStatus(), "[RETRY] 좀비 결제 정리: " + reason);

        outboxAppender.appendPaymentFailed(order.getId());

        log.warn("[좀비 결제 정리] 실패 처리. orderId={}, paymentId={}, reason={}",
                order.getId(), payment.getId(), reason);
        return ZombiePaymentRecoveryResult.FAILED_SYNCED;
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
