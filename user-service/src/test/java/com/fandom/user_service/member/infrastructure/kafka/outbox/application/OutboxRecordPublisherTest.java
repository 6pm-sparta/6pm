package com.fandom.user_service.member.infrastructure.kafka.outbox.application;

import com.fandom.user_service.member.infrastructure.kafka.KafkaTopics;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.OutboxStatus;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutbox;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutboxRepository;
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
    private UserOutboxRepository outboxRepository;

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

    private UserOutbox pendingRecord() {
        UserOutbox record = UserOutbox.builder()
                .aggregateId(aggregateId)
                .topic(KafkaTopics.USER_DELETED)
                .payload("{\"user_id\":\"" + aggregateId + "\"}")
                .build();
        ReflectionTestUtils.setField(record, "id", outboxId);
        return record;
    }

    @Test
    @DisplayName("발행 성공 시 PUBLISHED로 전이하고 publishedAt이 기록된다")
    void publishOne_success_marksPublished() {
        UserOutbox record = pendingRecord();
        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(record));
        given(kafkaTemplate.send(eq(KafkaTopics.USER_DELETED), eq(aggregateId.toString()), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        recordPublisher.publishOne(outboxId);

        assertThat(record.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(record.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("발행 실패 시 PENDING으로 남아 다음 폴링이 재시도할 수 있다")
    void publishOne_sendFails_staysPending() {
        UserOutbox record = pendingRecord();
        given(outboxRepository.findById(outboxId)).willReturn(Optional.of(record));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(), any(), any())).willReturn(failed);

        recordPublisher.publishOne(outboxId);

        assertThat(record.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(record.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("레코드가 없으면(이미 처리됨) 조용히 no-op 한다")
    void publishOne_recordNotFound_noOp() {
        given(outboxRepository.findById(outboxId)).willReturn(Optional.empty());

        recordPublisher.publishOne(outboxId); // 예외 없이 통과
    }
}
