package com.fandom.order_service.order.presentation.dto.response;

import com.fandom.order_service.order.domain.entity.Order;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 목록 조회 항목. 단건 조회(OrderResponse)보다 필드가 적은 요약 버전 (api 명세서 "주문 목록 조회" 기준).
 */
public record OrderSummaryResponse(
        UUID orderId,
        UUID seatId,
        String status,
        Long totalAmount,
        LocalDateTime createdAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getSeatId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
