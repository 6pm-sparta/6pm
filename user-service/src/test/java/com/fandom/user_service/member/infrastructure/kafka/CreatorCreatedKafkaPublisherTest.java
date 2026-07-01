package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.infrastructure.kafka.outbox.application.OutboxAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("크리에이터 생성 이벤트 발행기 단위 테스트 (Outbox 적재)")
class CreatorCreatedKafkaPublisherTest {

    @Mock
    private OutboxAppender outboxAppender;

    @InjectMocks
    private CreatorCreatedKafkaPublisher publisher;

    @Captor
    private ArgumentCaptor<CreatorCreatedMessage> messageCaptor;

    @Test
    @DisplayName("user.creator-created 토픽 + userId(aggregateId)로 Outbox에 적재된다")
    void publish() {
        UUID userId = UUID.randomUUID();

        publisher.publish(userId, "nick");

        then(outboxAppender).should()
                .append(eq(KafkaTopics.USER_CREATOR_CREATED), eq(userId), messageCaptor.capture());
        CreatorCreatedMessage message = messageCaptor.getValue();
        assertThat(message.userId()).isEqualTo(userId);
        assertThat(message.nickname()).isEqualTo("nick");
    }
}
