package com.fandom.order_service.kafka.consumer;

import com.fandom.order_service.kafka.KafkaTopics;
import com.fandom.order_service.kafka.event.SeatBookFailedEvent;
import com.fandom.order_service.kafka.event.SeatBookedEvent;
import com.fandom.order_service.order.application.compensation.OrderCompensationService;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatEventConsumer {

    private final OrderConfirmationService orderConfirmationService;
    private final OrderCompensationService orderCompensationService;

    @KafkaListener(topics = KafkaTopics.SEAT_BOOKED, containerFactory = "seatBookedKafkaListenerContainerFactory")
    public void onSeatBooked(SeatBookedEvent event) {
        log.info("[{}] 수신 orderId={}, seatId={}", KafkaTopics.SEAT_BOOKED, event.orderId(), event.seatId());
        orderConfirmationService.confirmOrder(event.orderId());
    }

    @KafkaListener(topics = KafkaTopics.SEAT_BOOK_FAILED, containerFactory = "seatBookFailedKafkaListenerContainerFactory")
    public void onSeatBookFailed(SeatBookFailedEvent event) {
        log.info("[{}] 수신 orderId={}, seatId={}, reason={}",
                KafkaTopics.SEAT_BOOK_FAILED, event.orderId(), event.seatId(), event.reason());
        orderCompensationService.compensate(event.orderId(), event.reason());
    }
}
