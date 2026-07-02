package com.fandom.order_service.kafka.outbox.application;

import com.fandom.order_service.kafka.outbox.domain.OrderOutbox;
import com.fandom.order_service.kafka.outbox.domain.OrderOutboxRepository;
import com.fandom.order_service.kafka.outbox.domain.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRecordPublisher 단위 테스트")
class OutboxRecordPublisherTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxRecordPublisher recordPublisher;

    private UUID outboxId;
    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        recordPublisher = new OutboxRecordPublisher(outboxRepository, kafkaTemplate, objectMapper);
        outboxId = UUID.randomUUID();
        aggregateId = UUID.randomUUID();
    }

    private OrderOutbox pendingRecord() {
        OrderOutbox record = OrderOutbox.builder()
                .aggregateId(aggregateId)
                .topic("order.payment.completed")
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .build();
        ReflectionTestUtils.setField(record, "id", outboxId);
        return record;
    }

    @Test
    @DisplayName("발행 성공 시 PUBLISHED로 전이하고 publishedAt이 기록된다")
    void publishOne_success_marksPublished() {
        // given
        OrderOutbox record = pendingRecord();
        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(record));
        given(kafkaTemplate.send(eq("order.payment.completed"), eq(aggregateId.toString()), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // when
        recordPublisher.publishOne(outboxId);

        // then
        assertThat(record.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(record.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("발행 실패 시 retryCount가 오르고 MAX 미만이면 PENDING 유지")
    void publishOne_sendFails_incrementsRetryCount_staysPending() {
        // given
        OrderOutbox record = pendingRecord();
        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(record));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        // when
        recordPublisher.publishOne(outboxId);

        // then
        assertThat(record.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("retryCount가 MAX_RETRY_COUNT에 도달하면 FAILED로 전이한다")
    void publishOne_retryExhausted_marksFailed() {
        // given
        OrderOutbox record = pendingRecord();
        ReflectionTestUtils.setField(record, "retryCount", OutboxRecordPublisher.MAX_RETRY_COUNT - 1);
        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(record));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        // when
        recordPublisher.publishOne(outboxId);

        // then
        assertThat(record.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(OutboxRecordPublisher.MAX_RETRY_COUNT);
    }

    @Test
    @DisplayName("레코드가 없으면(이미 처리됨) 조용히 no-op 한다")
    void publishOne_recordNotFound_noOp() {
        // given
        given(outboxRepository.findById(outboxId)).willReturn(Optional.empty());

        // when & then
        recordPublisher.publishOne(outboxId);
    }
}
