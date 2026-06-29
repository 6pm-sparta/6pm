package com.fandom.order_service.kafka.outbox.application;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.kafka.KafkaTopics;
import com.fandom.order_service.kafka.event.NotificationSendEvent;
import com.fandom.order_service.kafka.event.PaymentCancelledEvent;
import com.fandom.order_service.kafka.event.PaymentCompletedEvent;
import com.fandom.order_service.kafka.event.PaymentFailedEvent;
import com.fandom.order_service.kafka.outbox.domain.OrderOutbox;
import com.fandom.order_service.kafka.outbox.domain.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 발행할 이벤트를 Outbox에 적재한다. 반드시 상태 전이 Writer의 @Transactional 메서드 안에서 호출해야
 * 상태 변경과 같은 트랜잭션으로 커밋된다. 도메인 의미 메서드로 토픽/이벤트 조립을 캡슐화해 Writer가
 * Kafka 토픽이나 이벤트 record를 몰라도 되게 한다.
 *
 * 직렬화 실패는 던진다 — 발행 불가능한 이벤트면 트랜잭션을 롤백시켜 상태/이벤트 불일치를 막는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** 결제 승인 → 좌석 BOOKED (ticketing 수신). */
    public void appendPaymentCompleted(UUID orderId) {
        append(KafkaTopics.PAYMENT_COMPLETED, orderId, new PaymentCompletedEvent(orderId));
    }

    /** 결제 실패 → 좌석 해제 (ticketing 수신). */
    public void appendPaymentFailed(UUID orderId) {
        append(KafkaTopics.PAYMENT_FAILED, orderId, new PaymentFailedEvent(orderId));
    }

    /** 결제 취소/환불 완료 → 좌석 해제 (ticketing 수신). */
    public void appendPaymentCancelled(UUID orderId) {
        append(KafkaTopics.PAYMENT_CANCELLED, orderId, new PaymentCancelledEvent(orderId));
    }

    /** 예매 확정 알림 (notification 수신, ORDER_COMPLETED). */
    public void appendOrderCompletedNotification(UUID orderId, UUID userId) {
        append(KafkaTopics.NOTIFICATION_SEND, orderId,
                new NotificationSendEvent(orderId, "ORDER_COMPLETED",
                        "예매가 확정되었습니다", "주문하신 예매가 확정되었습니다.", List.of(userId)));
    }

    /** 주문 취소(환불 완료) 알림 (notification 수신, ORDER_CANCELED). */
    public void appendOrderCancelledNotification(UUID orderId, UUID userId) {
        append(KafkaTopics.NOTIFICATION_SEND, orderId,
                new NotificationSendEvent(orderId, "ORDER_CANCELED",
                        "주문이 취소되었습니다", "환불이 완료되었습니다.", List.of(userId)));
    }

    private void append(String topic, UUID aggregateId, Object event) {
        outboxRepository.save(
                OrderOutbox.builder()
                        .aggregateId(aggregateId)
                        .topic(topic)
                        .payload(serialize(event))
                        .build());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
