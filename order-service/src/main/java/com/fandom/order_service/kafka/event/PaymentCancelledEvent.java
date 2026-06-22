package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * order.payment.cancelled 발행 payload.
 * 발행 시점: PAID/CONFIRMED 주문이 유저 직접 취소로 환불까지 완료(REFUNDED)된 직후.
 */
public record PaymentCancelledEvent(UUID orderId) {
}
