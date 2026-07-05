package com.fandom.order_service.order.application.timeout;

import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 타임아웃 자동 취소 — 건당 트랜잭션.
 * OrderCancelWriter PENDING 분기와 동일한 동시성 원칙(비관적 락 + 재검증)을 따르되,
 * 상태 불일치는 예외가 아닌 SKIPPED로 처리한다(배치 처리이므로 경합 자체가 정상 결과).
 *
 * PAYMENT_REQUESTED가 PENDING에 흡수되면서, expired_at이 지났더라도 결제 요청이
 * 진행중(payments.REQUESTED)인 주문은 취소하지 않는다. 그대로 취소하면 직후 PG 승인 webhook이
 * 도착했을 때 "취소됐는데 결제는 승인됨" 정합성 버그가 생긴다. 이런 zombie 케이스(웹훅 유실)는
 * 이 스케줄러의 책임 밖이며, 별도 정리 로직이 필요하다(#297 작업 항목).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutWriter {

    private static final String TIMEOUT_REASON = "[USER] 주문 타임아웃 자동 취소";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public OrderTimeoutResult expireIfStillPending(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return OrderTimeoutResult.SKIPPED; // 이미 다른 경로로 상태 변경됨 — 정상 경합
        }

        if (paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)) {
            // 결제 요청이 진행중 — webhook 결과를 기다린다. zombie 정리는 별도 로직 책임.
            return OrderTimeoutResult.SKIPPED;
        }

        OrderStatus before = order.getStatus();
        order.markCancelled();

        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .orderId(order.getId())
                        .fromStatus(before)
                        .toStatus(order.getStatus())
                        .reason(TIMEOUT_REASON)
                        .build());

        outboxAppender.appendHoldReleased(order.getId());

        return OrderTimeoutResult.CANCELLED;
    }
}
