package com.fandom.ticketing_service.kafka.consumer;

import com.fandom.ticketing_service.kafka.KafkaTopics;
import com.fandom.ticketing_service.kafka.event.HoldReleasedEvent;
import com.fandom.ticketing_service.kafka.event.PaymentCompletedEvent;
import com.fandom.ticketing_service.kafka.event.PaymentFailedEvent;
import com.fandom.ticketing_service.seat.application.SeatConfirmService;
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

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(@Payload String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        log.info("Consumed order.payment.completed: orderId={}", event.orderId());
        seatConfirmService.confirmSeat(event.orderId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    public void onPaymentFailed(@Payload String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        log.info("Consumed order.payment.failed: orderId={}", event.orderId());
        seatConfirmService.releaseSeat(event.orderId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_CANCELLED)
    public void onPaymentCancelled(@Payload String payload) throws Exception {
        PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
        log.info("Consumed order.payment.cancelled: orderId={}", event.orderId());
        seatConfirmService.releaseSeat(event.orderId());
    }

    /**
     * PENDING(결제 전) 주문 취소 — 유저 직접 취소 또는 타임아웃 자동 취소 둘 다 이 토픽으로 발행된다.
     * releaseHold()로 ticketing이 먼저 해제를 트리거한 경우엔 이미 좌석이 비어있어 releaseSeat이 멱등하게 처리한다.
     */
    @KafkaListener(topics = KafkaTopics.HOLD_RELEASED)
    public void onHoldReleased(@Payload String payload) throws Exception {
        HoldReleasedEvent event = objectMapper.readValue(payload, HoldReleasedEvent.class);
        log.info("Consumed order.hold.released: orderId={}", event.orderId());
        seatConfirmService.releaseSeat(event.orderId());
    }
}
