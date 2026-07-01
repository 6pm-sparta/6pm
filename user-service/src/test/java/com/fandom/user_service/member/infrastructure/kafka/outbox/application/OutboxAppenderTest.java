package com.fandom.user_service.member.infrastructure.kafka.outbox.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.infrastructure.kafka.FollowEventMessage;
import com.fandom.user_service.member.infrastructure.kafka.KafkaTopics;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.OutboxStatus;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutbox;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxAppender 단위 테스트")
class OutboxAppenderTest {

    @Mock
    private UserOutboxRepository outboxRepository;

    @Captor
    private ArgumentCaptor<UserOutbox> outboxCaptor;

    // 직렬화 형태(@JsonProperty snake_case 매핑)를 실제로 검증하기 위해 진짜 ObjectMapper 사용
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxAppender outboxAppender;

    private UUID followId;

    @BeforeEach
    void setUp() {
        outboxAppender = new OutboxAppender(outboxRepository, objectMapper);
        followId = UUID.randomUUID();
    }

    private UserOutbox captureSaved() {
        then(outboxRepository).should().save(outboxCaptor.capture());
        return outboxCaptor.getValue();
    }

    @Test
    @DisplayName("append: topic + aggregateId + PENDING 상태로 적재한다")
    void append_savesPendingRecord() {
        FollowEventMessage message =
                new FollowEventMessage(followId, UUID.randomUUID(), UUID.randomUUID(), "nick");

        outboxAppender.append(KafkaTopics.USER_FOLLOWED, followId, message);

        UserOutbox saved = captureSaved();
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.USER_FOLLOWED);
        assertThat(saved.getAggregateId()).isEqualTo(followId);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("append: payload는 @JsonProperty(snake_case) 매핑이 적용돼 직렬화된다")
    void append_serializesSnakeCasePayload() throws Exception {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        FollowEventMessage message = new FollowEventMessage(followId, followerId, followeeId, "nick");

        outboxAppender.append(KafkaTopics.USER_FOLLOWED, followId, message);

        UserOutbox saved = captureSaved();
        JsonNode payload = objectMapper.readTree(saved.getPayload());
        // consumer가 동일하게 읽으려면 snake_case 필드여야 한다
        assertThat(payload.get("follow_id").asText()).isEqualTo(followId.toString());
        assertThat(payload.get("follower_id").asText()).isEqualTo(followerId.toString());
        assertThat(payload.get("followee_id").asText()).isEqualTo(followeeId.toString());
        assertThat(payload.get("nickname").asText()).isEqualTo("nick");
    }

    @Test
    @DisplayName("append: 직렬화 실패 시 CustomException을 던져 트랜잭션을 롤백시킨다")
    void append_serializationFails_throws() throws Exception {
        // ObjectMapper.writeValueAsString이 실패하도록 mock으로 교체
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        given(failingMapper.writeValueAsString(any()))
                .willThrow(new JsonProcessingException("boom") {});
        OutboxAppender appender = new OutboxAppender(outboxRepository, failingMapper);

        assertThatThrownBy(() -> appender.append(KafkaTopics.USER_DELETED, followId, new Object()))
                .isInstanceOf(CustomException.class);

        // 직렬화 실패 시 저장도 시도되지 않아야 한다
        then(outboxRepository).shouldHaveNoInteractions();
    }
}
