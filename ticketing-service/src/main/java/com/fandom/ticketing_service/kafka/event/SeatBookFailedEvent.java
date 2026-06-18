package com.fandom.ticketing_service.kafka.event;

import java.util.UUID;

public record SeatBookFailedEvent(UUID orderId, UUID seatId, String reason) {
}
