package com.fandom.order_service.order.application.timeout;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 타임아웃 자동 취소 — 건당 트랜잭션.
 * 상태 불일치는 예외가 아닌 SKIPPED로 처리한다(배치 처리이므로 경합 자체가 정상 결과).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutWriter {

    private static final String TIMEOUT_REASON = "주문 타임아웃 자동 취소";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    public OrderTimeoutResult expireIfStillPending(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return OrderTimeoutResult.SKIPPED; // 이미 다른 경로로 상태 변경됨 — 정상 경합
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

        return OrderTimeoutResult.CANCELLED;
    }
}
