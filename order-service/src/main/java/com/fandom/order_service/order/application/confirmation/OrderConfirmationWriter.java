package com.fandom.order_service.order.application.confirmation;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ticketing.seat.booked 수신 처리. 다른 Writer들과 동일한 이유로 Service와 물리적으로 분리한다
 * (self-invocation은 Spring AOP 프록시를 거치지 않아 @Transactional 경계가 생기지 않음).
 *
 * 비관적 락(findByIdForUpdate)으로 조회하는 이유: 유저 직접 취소(OrderCancelWriter)와 이 컨슈머가
 * 동시에 같은 주문을 건드릴 수 있다 — 예를 들어 유저가 PAID 상태에서 취소 요청을 보낸 거의 같은
 * 시점에 좌석 확정 이벤트가 도착하는 경우. 락으로 한쪽만 먼저 통과시키고, 나중에 들어온 쪽은
 * 이미 바뀐 상태를 보고 분기(ALREADY_CONFIRMED/SKIPPED_INVALID_STATE)된다.
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmationWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public OrderConfirmationResult confirm(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return OrderConfirmationResult.alreadyConfirmed(order.getId());
        }

        if (order.getStatus() != OrderStatus.CONFIRMING) {
            return OrderConfirmationResult.skippedInvalidState(order.getId());
        }

        OrderStatus before = order.getStatus();
        order.markConfirmed();
        saveHistory(order.getId(), before, order.getStatus(), "[USER] 좌석 예매 확정");
        outboxAppender.appendOrderCompletedNotification(order.getId(), order.getUserId());

        return OrderConfirmationResult.confirmed(order.getId(), order.getUserId());
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
