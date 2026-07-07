package com.fandom.ticketing_service.kafka;

/**
 * ticketing-service가 발행/수신하는 Kafka 토픽 모음.
 */
public final class KafkaTopics {

    /** 발행(Producer): ticketing-service → order-service */
    public static final String SEAT_BOOKED = "ticketing.seat.booked";
    public static final String SEAT_BOOK_FAILED = "ticketing.seat.book.failed";

    /** 수신(Consumer): order-service → ticketing-service */
    public static final String PAYMENT_COMPLETED = "order.payment.completed";
    public static final String PAYMENT_FAILED = "order.payment.failed";
    public static final String PAYMENT_CANCELLED = "order.payment.cancelled";
    public static final String HOLD_RELEASED = "order.hold.released";

    private KafkaTopics() {
    }
}
