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
@DisplayName("팔로우 이벤트 발행기 단위 테스트 (Outbox 적재)")
class FollowEventKafkaPublisherTest {

    @Mock
    private OutboxAppender outboxAppender;

    @InjectMocks
    private FollowEventKafkaPublisher publisher;

    @Captor
    private ArgumentCaptor<FollowEventMessage> messageCaptor;

    @Test
    @DisplayName("팔로우 이벤트는 user.followed 토픽 + followId(aggregateId)로 Outbox에 적재된다")
    void publishFollowed() {
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        publisher.publishFollowed(followId, followerId, followeeId, "member");

        then(outboxAppender).should()
                .append(eq(KafkaTopics.USER_FOLLOWED), eq(followId), messageCaptor.capture());
        FollowEventMessage message = messageCaptor.getValue();
        assertThat(message.followId()).isEqualTo(followId);
        assertThat(message.followerId()).isEqualTo(followerId);
        assertThat(message.followeeId()).isEqualTo(followeeId);
        assertThat(message.nickname()).isEqualTo("member");
    }

    @Test
    @DisplayName("언팔로우 이벤트는 user.unfollowed 토픽 + followId로 Outbox에 적재된다 (nickname null)")
    void publishUnfollowed() {
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        publisher.publishUnfollowed(followId, followerId, followeeId);

        then(outboxAppender).should()
                .append(eq(KafkaTopics.USER_UNFOLLOWED), eq(followId), messageCaptor.capture());
        FollowEventMessage message = messageCaptor.getValue();
        assertThat(message.followId()).isEqualTo(followId);
        assertThat(message.nickname()).isNull();
    }
}
