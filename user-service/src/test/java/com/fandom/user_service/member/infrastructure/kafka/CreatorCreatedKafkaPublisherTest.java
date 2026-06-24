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
@DisplayName("크리에이터 생성 Kafka 발행기 단위 테스트")
class CreatorCreatedKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, CreatorCreatedMessage> kafkaTemplate;

    @InjectMocks
    private CreatorCreatedKafkaPublisher publisher;

    @Test
    @DisplayName("크리에이터 생성 이벤트는 user.creator-created 토픽에 userId를 key로 발행된다")
    void publish() {
        UUID userId = UUID.randomUUID();
        String nickname = "creator";
        ArgumentCaptor<CreatorCreatedMessage> messageCaptor = ArgumentCaptor.forClass(CreatorCreatedMessage.class);
        given(kafkaTemplate.send(anyString(), anyString(), any(CreatorCreatedMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publish(userId, nickname);

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_CREATOR_CREATED), eq(userId.toString()), messageCaptor.capture());
        assertThat(messageCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(messageCaptor.getValue().nickname()).isEqualTo(nickname);
    }
}
