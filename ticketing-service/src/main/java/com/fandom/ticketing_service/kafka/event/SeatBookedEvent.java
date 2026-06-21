package com.fandom.ticketing_service.kafka.event;

import java.util.UUID;

public record SeatBookedEvent(UUID orderId, UUID seatId) {
}
