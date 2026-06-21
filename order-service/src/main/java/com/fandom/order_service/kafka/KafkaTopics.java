package com.fandom.order_service.kafka;

/**
 * order-service가 발행/수신하는 Kafka 토픽 모음. order-service 설계 문서 "4. Kafka 이벤트" 기준.
 */
public final class KafkaTopics {

    /** 발행(Producer): order-service → ticketing-service */
    public static final String PAYMENT_COMPLETED = "order.payment.completed";
    public static final String PAYMENT_FAILED = "order.payment.failed";
    public static final String PAYMENT_CANCELLED = "order.payment.cancelled";

    /** 발행(Producer): order-service → notification-service */
    public static final String NOTIFICATION_SEND = "notification.send";

    /** 수신(Consumer): ticketing-service → order-service */
    public static final String SEAT_BOOKED = "ticketing.seat.booked";

    private KafkaTopics() {
    }
}
