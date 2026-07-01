package com.fandom.feed.infra.kafka;

import com.fandom.feed.global.constant.NotificationPolicy;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
import com.fandom.feed.infra.kafka.payload.NotificationSendPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {
    @Mock
    KafkaTemplate<String, NotificationSendPayload> kafkaTemplate;

    @InjectMocks
    NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("정상 동작 - 닉네임을 포함한 알람 payload를 올바른 토픽과 키로 발행")
    void publishChunk() {
        // given
        UUID postId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        List<UUID> followerChunk = List.of(UUID.randomUUID());
        String nickname = "닉네임";

        CompletableFuture<SendResult<String, NotificationSendPayload>> future = new CompletableFuture<>();
        given(kafkaTemplate.send(eq(KafkaTopic.NOTIFICATION_SEND), eq(postId.toString()), any())).willReturn(future);

        // when
        notificationPublisher.publishChunk(postId, nickname, cursor, followerChunk);

        // then
        ArgumentCaptor<NotificationSendPayload> captor = ArgumentCaptor.forClass(NotificationSendPayload.class);
        verify(kafkaTemplate).send(eq(KafkaTopic.NOTIFICATION_SEND), eq(postId.toString()), captor.capture());

        NotificationSendPayload payload = captor.getValue();
        assertThat(payload.referenceId()).isEqualTo(postId);
        assertThat(payload.type()).isEqualTo(NotificationPolicy.POST_CREATED);
        assertThat(payload.title()).isEqualTo(nickname + NotificationPolicy.POST_CREATED_TITLE);
        assertThat(payload.content()).isNull();
        assertThat(payload.targetUserIds()).isEqualTo(followerChunk);
    }

    @Test
    @DisplayName("발행 실패 - 예외를 호출자에게 전파하지 않음")
    void publishChunkDoesNotThrowOnFailure() {
        // given
        UUID postId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        List<UUID> followerChunk = List.of(UUID.randomUUID());

        CompletableFuture<SendResult<String, NotificationSendPayload>> future = new CompletableFuture<>();
        given(kafkaTemplate.send(any(String.class), any(String.class), any())).willReturn(future);

        // when
        assertDoesNotThrow(() -> {
            notificationPublisher.publishChunk(postId, "닉네임", cursor, followerChunk);
            future.completeExceptionally(new RuntimeException("Kafka 발행 실패"));
        });
    }
}