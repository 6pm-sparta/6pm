package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.infrastructure.kafka.outbox.application.OutboxAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 탈퇴 이벤트 발행기 단위 테스트 (Outbox 적재)")
class UserWithdrawalKafkaPublisherTest {

    @Mock
    private OutboxAppender outboxAppender;

    @InjectMocks
    private UserWithdrawalKafkaPublisher publisher;

    @Test
    @DisplayName("CREATOR 탈퇴: creator-withdrawn + 공통 user.deleted 두 토픽을 적재한다")
    void publish_creator() {
        UUID userId = UUID.randomUUID();

        publisher.publish(userId, Role.CREATOR);

        // 역할별 정리 이벤트
        then(outboxAppender).should().append(eq(KafkaTopics.USER_CREATOR_WITHDRAWN), eq(userId), any());
        // 공통 탈퇴 이벤트
        then(outboxAppender).should().append(eq(KafkaTopics.USER_DELETED), eq(userId), any());
        // MEMBER 토픽은 발행되지 않아야 한다
        then(outboxAppender).should(never()).append(eq(KafkaTopics.USER_MEMBER_WITHDRAWN), any(), any());
    }

    @Test
    @DisplayName("MEMBER 탈퇴: member-withdrawn + 공통 user.deleted 두 토픽을 적재한다")
    void publish_member() {
        UUID userId = UUID.randomUUID();

        publisher.publish(userId, Role.MEMBER);

        then(outboxAppender).should().append(eq(KafkaTopics.USER_MEMBER_WITHDRAWN), eq(userId), any());
        then(outboxAppender).should().append(eq(KafkaTopics.USER_DELETED), eq(userId), any());
        then(outboxAppender).should(never()).append(eq(KafkaTopics.USER_CREATOR_WITHDRAWN), any(), any());
    }
}
