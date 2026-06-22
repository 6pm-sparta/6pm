package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * order.payment.completed 발행 payload. 좌석을 BOOKED로 전환하는 데 쓰인다.
 */
public record PaymentCompletedEvent(UUID orderId) {
}
