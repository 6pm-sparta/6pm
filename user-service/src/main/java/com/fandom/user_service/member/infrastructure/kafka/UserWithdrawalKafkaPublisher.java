package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.MemberWithdrawalEventPublisher;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.infrastructure.kafka.outbox.application.OutboxAppender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * MemberWithdrawalEventPublisher(port) 구현체. 발행은 Transactional Outbox를 통한다.
 * 탈퇴 1회에 역할별 withdrawn(member 또는 creator) + 공통 user.deleted를 함께 적재한다.
 * 도메인 트랜잭션과 같은 트랜잭션에서 Outbox에 적재되고, 실제 Kafka 발행은 OutboxPublisher 폴링이 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawalKafkaPublisher implements MemberWithdrawalEventPublisher {

    private final OutboxAppender outboxAppender;

    @Override
    public void publish(UUID userId, Role role) {
        UserWithdrawalMessage message = new UserWithdrawalMessage(userId);

        // 역할별 정리 이벤트 (feed 등 downstream)
        if (role == Role.CREATOR) {
            outboxAppender.append(KafkaTopics.USER_CREATOR_WITHDRAWN, userId, message);
        } else if (role == Role.MEMBER) {
            outboxAppender.append(KafkaTopics.USER_MEMBER_WITHDRAWN, userId, message);
        }
        // 공통 탈퇴 이벤트 (auth/chat/notification — 토큰 무효화 등)
        outboxAppender.append(KafkaTopics.USER_DELETED, userId, message);
    }
}
