package com.fandom.ticketing_service.order.dto;

import java.util.UUID;

public record CreateOrderRequest(
        UUID userId,
        Long showId,
        UUID showSeatId,
        int price
) {
}
