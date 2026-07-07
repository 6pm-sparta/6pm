package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * ticketing.seat.book.failed 수신 payload.
 */
public record SeatBookFailedEvent(UUID orderId, UUID seatId, String reason) {
}
