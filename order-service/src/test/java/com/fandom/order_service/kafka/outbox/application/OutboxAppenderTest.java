package com.fandom.order_service.kafka.outbox.application;

import com.fandom.order_service.kafka.KafkaTopics;
import com.fandom.order_service.kafka.outbox.domain.OrderOutbox;
import com.fandom.order_service.kafka.outbox.domain.OrderOutboxRepository;
import com.fandom.order_service.kafka.outbox.domain.OutboxStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxAppender 단위 테스트")
class OutboxAppenderTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Captor
    private ArgumentCaptor<OrderOutbox> outboxCaptor;

    // 직렬화 형태(@JsonProperty 매핑)를 실제로 검증하기 위해 진짜 ObjectMapper 사용
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxAppender outboxAppender;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        outboxAppender = new OutboxAppender(outboxRepository, objectMapper);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private OrderOutbox captureSaved() {
        then(outboxRepository).should().save(outboxCaptor.capture());
        return outboxCaptor.getValue();
    }

    @Test
    @DisplayName("appendPaymentCompleted: completed 토픽 + PENDING 상태 + orderId payload로 적재한다")
    void appendPaymentCompleted() throws Exception {
        outboxAppender.appendPaymentCompleted(orderId);

        OrderOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.PAYMENT_COMPLETED);
        assertThat(saved.getAggregateId()).isEqualTo(orderId);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
    }

    @Test
    @DisplayName("appendPaymentFailed: failed 토픽으로 적재한다")
    void appendPaymentFailed() {
        outboxAppender.appendPaymentFailed(orderId);

        OrderOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.PAYMENT_FAILED);
        assertThat(saved.getAggregateId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("appendPaymentCancelled: cancelled 토픽으로 적재한다")
    void appendPaymentCancelled() {
        outboxAppender.appendPaymentCancelled(orderId);

        OrderOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.PAYMENT_CANCELLED);
        assertThat(saved.getAggregateId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("appendOrderCompletedNotification: notification.send 토픽 + snake_case payload로 적재한다")
    void appendOrderCompletedNotification() throws Exception {
        outboxAppender.appendOrderCompletedNotification(orderId, userId);

        OrderOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.NOTIFICATION_SEND);

        // @JsonProperty 매핑이 적용된 snake_case 필드로 직렬화돼야 consumer가 동일하게 읽는다
        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("reference_id").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("type").asText()).isEqualTo("ORDER_COMPLETED");
        assertThat(payload.get("target_user_ids").get(0).asText()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("appendOrderCancelledNotification: type이 ORDER_CANCELED로 적재된다")
    void appendOrderCancelledNotification() throws Exception {
        outboxAppender.appendOrderCancelledNotification(orderId, userId);

        OrderOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.NOTIFICATION_SEND);

        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("ORDER_CANCELED");
        assertThat(payload.get("reference_id").asText()).isEqualTo(orderId.toString());
    }
}
