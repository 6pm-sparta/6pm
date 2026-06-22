package com.fandom.ticketing_service.kafka.consumer;

import com.fandom.ticketing_service.kafka.event.PaymentCompletedEvent;
import com.fandom.ticketing_service.kafka.event.PaymentFailedEvent;
import com.fandom.ticketing_service.seat.service.SeatConfirmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final SeatConfirmService seatConfirmService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.payment.completed")
    public void onPaymentCompleted(@Payload String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        log.info("Consumed order.payment.completed: orderId={}", event.orderId());
        seatConfirmService.confirmSeat(event.orderId());
    }

    @KafkaListener(topics = "order.payment.failed")
    public void onPaymentFailed(@Payload String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        log.info("Consumed order.payment.failed: orderId={}", event.orderId());
        seatConfirmService.releaseSeat(event.orderId());
    }

    @KafkaListener(topics = "order.payment.cancelled")
    public void onPaymentCancelled(@Payload String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        log.info("Consumed order.payment.cancelled: orderId={}", event.orderId());
        seatConfirmService.releaseSeat(event.orderId());
    }
}
