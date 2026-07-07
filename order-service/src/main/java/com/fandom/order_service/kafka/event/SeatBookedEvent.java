package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * ticketing.seat.booked 수신 payload.
 */
public record SeatBookedEvent(UUID orderId, UUID seatId) {
}
