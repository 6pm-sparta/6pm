package com.fandom.user_service.member.infrastructure.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowEventKafkaPublisher unit tests")
class FollowEventKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, FollowEventMessage> kafkaTemplate;

    @InjectMocks
    private FollowEventKafkaPublisher publisher;

    @Test
    @DisplayName("follow event publishes user.followed with follow id key")
    void publishFollowed() {
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        ArgumentCaptor<FollowEventMessage> messageCaptor = ArgumentCaptor.forClass(FollowEventMessage.class);
        given(kafkaTemplate.send(anyString(), anyString(), any(FollowEventMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publishFollowed(followId, followerId, followeeId);

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_FOLLOWED), eq(followId.toString()), messageCaptor.capture());
        assertThat(messageCaptor.getValue().followId()).isEqualTo(followId);
        assertThat(messageCaptor.getValue().followerId()).isEqualTo(followerId);
        assertThat(messageCaptor.getValue().followeeId()).isEqualTo(followeeId);
    }

    @Test
    @DisplayName("unfollow event publishes user.unfollowed with follow id key")
    void publishUnfollowed() {
        UUID followId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        ArgumentCaptor<FollowEventMessage> messageCaptor = ArgumentCaptor.forClass(FollowEventMessage.class);
        given(kafkaTemplate.send(anyString(), anyString(), any(FollowEventMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publishUnfollowed(followId, followerId, followeeId);

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_UNFOLLOWED), eq(followId.toString()), messageCaptor.capture());
        assertThat(messageCaptor.getValue().followId()).isEqualTo(followId);
        assertThat(messageCaptor.getValue().followerId()).isEqualTo(followerId);
        assertThat(messageCaptor.getValue().followeeId()).isEqualTo(followeeId);
    }
}
