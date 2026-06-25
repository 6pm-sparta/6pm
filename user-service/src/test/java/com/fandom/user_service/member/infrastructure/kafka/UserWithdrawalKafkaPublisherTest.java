package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.domain.entity.Role;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 탈퇴 Kafka 발행기 단위 테스트")
class UserWithdrawalKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, UserWithdrawalMessage> kafkaTemplate;

    @InjectMocks
    private UserWithdrawalKafkaPublisher publisher;

    @Test
    @DisplayName("일반 회원 탈퇴는 member-withdrawn과 deleted 이벤트를 발행한다")
    void publish_member() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<UserWithdrawalMessage> messageCaptor = ArgumentCaptor.forClass(UserWithdrawalMessage.class);
        given(kafkaTemplate.send(anyString(), anyString(), any(UserWithdrawalMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publish(userId, Role.MEMBER);

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_MEMBER_WITHDRAWN), eq(userId.toString()), messageCaptor.capture());
        verify(kafkaTemplate).send(eq(KafkaTopics.USER_DELETED), eq(userId.toString()), any(UserWithdrawalMessage.class));
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.USER_CREATOR_WITHDRAWN), anyString(), any(UserWithdrawalMessage.class));
        assertThat(messageCaptor.getValue().userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("크리에이터 탈퇴는 creator-withdrawn과 deleted 이벤트를 발행한다")
    void publish_creator() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<UserWithdrawalMessage> messageCaptor = ArgumentCaptor.forClass(UserWithdrawalMessage.class);
        given(kafkaTemplate.send(anyString(), anyString(), any(UserWithdrawalMessage.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publish(userId, Role.CREATOR);

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_CREATOR_WITHDRAWN), eq(userId.toString()), messageCaptor.capture());
        verify(kafkaTemplate).send(eq(KafkaTopics.USER_DELETED), eq(userId.toString()), any(UserWithdrawalMessage.class));
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.USER_MEMBER_WITHDRAWN), anyString(), any(UserWithdrawalMessage.class));
        assertThat(messageCaptor.getValue().userId()).isEqualTo(userId);
    }
}
