package com.fandom.order_service.order.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Ticketing → Order 내부 호출(Feign) 전용 주문 생성 요청.
 */
public record CreateOrderRequest(

        @NotNull(message = "holdId는 필수입니다.")
        UUID holdId,

        @NotNull(message = "seatId는 필수입니다.")
        UUID seatId,

        @NotNull(message = "userId는 필수입니다.")
        UUID userId,

        @NotNull(message = "totalAmount는 필수입니다.")
        @Positive(message = "totalAmount는 0보다 커야 합니다.")
        Long totalAmount
) {
}
