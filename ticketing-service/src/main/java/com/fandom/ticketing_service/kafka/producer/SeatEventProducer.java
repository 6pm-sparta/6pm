package com.fandom.ticketing_service.kafka.producer;

import com.fandom.ticketing_service.kafka.KafkaTopics;
import com.fandom.ticketing_service.kafka.event.SeatBookFailedEvent;
import com.fandom.ticketing_service.kafka.event.SeatBookedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 발행 실패는 여기서 흡수하고 로그만 남긴다(throw하지 않음) — 좌석 Hold/확정은 이미 DB에 반영된
 * 뒤이므로, Kafka 장애가 그 응답 자체를 실패시키게 두면 안 된다(order-service OrderEventProducer와 동일 정책).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSeatBooked(SeatBookedEvent event) {
        send(KafkaTopics.SEAT_BOOKED, event.orderId().toString(), event);
    }

    public void publishSeatBookFailed(SeatBookFailedEvent event) {
        send(KafkaTopics.SEAT_BOOK_FAILED, event.orderId().toString(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka 발행 실패] topic={}, key={}", topic, key, ex);
                } else {
                    log.info("[Kafka 발행 성공] topic={}, key={}, partition={}, offset={}",
                            topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (Exception unexpected) {
            log.error("[Kafka 발행 실패] topic={}, key={}", topic, key, unexpected);
        }
    }
}
