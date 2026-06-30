package com.fandom.ticketing_service.order.infrastructure.dto;

import java.util.UUID;

/**
 * order-service의 InternalOrderController가 받는 요청 DTO와 필드를 맞춘다.
 * holdId는 order-service 측 1차 멱등성 방어(Redis)에 쓰이는 키.
 */
public record CreateOrderRequest(
        UUID holdId,
        UUID seatId,
        UUID userId,
        Long totalAmount
) {
}
