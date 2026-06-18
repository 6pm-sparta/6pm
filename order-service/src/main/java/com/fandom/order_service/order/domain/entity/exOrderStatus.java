package com.fandom.order_service.order.domain.entity;

public enum OrderStatus {
    PENDING,
    PAYMENT_REQUESTED,
    PAID,
    CONFIRMED,
    COMPENSATING,
    REFUND_REQUESTED,
    CANCELLED,
    REFUNDED,
    FAILED
}
