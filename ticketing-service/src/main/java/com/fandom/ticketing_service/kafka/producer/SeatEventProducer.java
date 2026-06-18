package com.fandom.ticketing_service.kafka.producer;

import com.fandom.ticketing_service.kafka.event.SeatBookFailedEvent;
import com.fandom.ticketing_service.kafka.event.SeatBookedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatEventProducer {

    private static final String SEAT_BOOKED_TOPIC = "ticketing.seat.booked";
    private static final String SEAT_BOOK_FAILED_TOPIC = "ticketing.seat.book.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSeatBooked(SeatBookedEvent event) {
        kafkaTemplate.send(SEAT_BOOKED_TOPIC, event.orderId().toString(), event);
        log.info("Published seat.booked: orderId={}, seatId={}", event.orderId(), event.seatId());
    }

    public void publishSeatBookFailed(SeatBookFailedEvent event) {
        kafkaTemplate.send(SEAT_BOOK_FAILED_TOPIC, event.orderId().toString(), event);
        log.info("Published seat.book.failed: orderId={}, reason={}", event.orderId(), event.reason());
    }
}
