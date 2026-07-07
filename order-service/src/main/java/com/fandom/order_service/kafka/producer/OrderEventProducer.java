package com.fandom.order_service.kafka.producer;

import com.fandom.order_service.kafka.KafkaTopics;
import com.fandom.order_service.kafka.event.NotificationSendEvent;
import com.fandom.order_service.kafka.event.PaymentCancelledEvent;
import com.fandom.order_service.kafka.event.PaymentCompletedEvent;
import com.fandom.order_service.kafka.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * order-service가 발행하는 이벤트 모음.
 *
 * 호출 시점 주의: 반드시 DB 트랜잭션 커밋 이후(Writer 메서드 호출이 끝난 뒤)에만 호출해야 한다.
 * 커밋 전에 발행하면 트랜잭션이 롤백돼도 이벤트는 이미 나가버리는 문제가 생긴다.
 *
 * 발행 실패는 이 클래스 안에서 잡아서 로그만 남기고 끝낸다(throw하지 않음) — DB 상태는 이미
 * 정상 커밋된 뒤이므로, Kafka 장애가 결제승인/취소/확정 API 응답 자체를 실패시키게 두면 안 된다.
 * send()가 즉시 던지는 예외(producer 종료 등)와, max.block.ms 초과로 블로킹 중 던지는 예외 둘 다 여기서 흡수한다.
 *
 * 알려진 리스크(Outbox 패턴 미적용): 위 fail-soft 처리는 "API 실패로 전파되는 것"만 막을 뿐,
 * 이벤트 자체가 영구히 누락되는 문제는 해결하지 못한다. DB 커밋과 Kafka 발행이 같은 트랜잭션으로
 * 묶이지 않은 별도 단계라, 발행이 실패하면(혹은 커밋 직후 프로세스가 죽으면) 이벤트는 그냥 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(UUID orderId) {
        send(KafkaTopics.PAYMENT_COMPLETED, orderId.toString(), new PaymentCompletedEvent(orderId));
    }

    public void publishPaymentFailed(UUID orderId) {
        send(KafkaTopics.PAYMENT_FAILED, orderId.toString(), new PaymentFailedEvent(orderId));
    }

    public void publishPaymentCancelled(UUID orderId) {
        send(KafkaTopics.PAYMENT_CANCELLED, orderId.toString(), new PaymentCancelledEvent(orderId));
    }

    /**
     * 예매 확정(CONFIRMED) 알림. notification-service의 NotificationType에 이미 ORDER_COMPLETED가
     * 정의돼 있어 그 값을 그대로 사용한다. 별도 order.confirmed 토픽은 쓰지 않는다 — 그런 토픽은
     * 실제로 존재하지 않고, notification.send가 notification-service로 가는 유일한 통로다.
     */
    public void publishOrderCompletedNotification(UUID orderId, UUID userId) {
        send(KafkaTopics.NOTIFICATION_SEND, orderId.toString(),
                new NotificationSendEvent(orderId, "ORDER_COMPLETED",
                        "예매가 확정되었습니다", "주문하신 예매가 확정되었습니다.", List.of(userId)));
    }

    /** 주문 취소(환불 완료) 알림. notification-service의 NotificationType.ORDER_CANCELED에 대응. */
    public void publishOrderCancelledNotification(UUID orderId, UUID userId) {
        send(KafkaTopics.NOTIFICATION_SEND, orderId.toString(),
                new NotificationSendEvent(orderId, "ORDER_CANCELED",
                        "주문이 취소되었습니다", "환불이 완료되었습니다.", List.of(userId)));
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
            // send() 호출 자체가 동기적으로 던지는 경우(producer 종료, max.block.ms 초과 등)까지 방어.
            log.error("[Kafka 발행 실패] topic={}, key={}", topic, key, unexpected);
        }
    }
}
