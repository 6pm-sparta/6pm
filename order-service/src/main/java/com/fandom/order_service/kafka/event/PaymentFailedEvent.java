package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * order.payment.failed 발행 payload. 선점된 좌석을 해제하는 데 쓰인다.
 */
public record PaymentFailedEvent(UUID orderId) {
}
