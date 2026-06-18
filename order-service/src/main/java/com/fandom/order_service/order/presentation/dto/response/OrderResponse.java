package com.fandom.order_service.order.presentation.dto.response;

import com.fandom.order_service.order.domain.entity.Order;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID seatId,
        UUID userId,
        String status,
        Long totalAmount,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getSeatId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
