package com.fandom.order_service.order.presentation.dto.response;

import com.fandom.order_service.order.domain.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 취소 응답.
 * paymentId는 환불이 발생한 경우(PAID/CONFIRMED 취소)에만 채워진다.
 */
public record OrderCancelResponse(
        UUID orderId,
        String status,
        UUID paymentId,
        LocalDateTime updatedAt
) {
    public static OrderCancelResponse withoutRefund(UUID orderId, OrderStatus status, LocalDateTime updatedAt) {
        return new OrderCancelResponse(orderId, status.name(), null, updatedAt);
    }

    public static OrderCancelResponse refunded(UUID orderId, OrderStatus status, UUID paymentId, LocalDateTime updatedAt) {
        return new OrderCancelResponse(orderId, status.name(), paymentId, updatedAt);
    }
}
