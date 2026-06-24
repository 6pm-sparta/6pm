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

    /**
     * PG에 비동기 환불 요청을 접수시킨 직후 응답한다.
     */
    public static OrderCancelResponse refundRequested(UUID orderId, UUID paymentId, LocalDateTime updatedAt) {
        return new OrderCancelResponse(orderId, OrderStatus.REFUND_REQUESTED.name(), paymentId, updatedAt);
    }
}