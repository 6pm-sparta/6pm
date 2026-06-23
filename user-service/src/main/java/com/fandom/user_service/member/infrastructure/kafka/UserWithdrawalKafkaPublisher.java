package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.MemberWithdrawalEventPublisher;
import com.fandom.user_service.member.domain.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserWithdrawalKafkaPublisher implements MemberWithdrawalEventPublisher {

    private final KafkaTemplate<String, UserWithdrawalMessage> kafkaTemplate;

    @Override
    public void publish(UUID userId, Role role) {
        UserWithdrawalMessage message = new UserWithdrawalMessage(userId);
        String key = userId.toString();

        if (role == Role.CREATOR) {
            kafkaTemplate.send(KafkaTopics.USER_CREATOR_WITHDRAWN, key, message);
        } else if (role == Role.MEMBER) {
            kafkaTemplate.send(KafkaTopics.USER_MEMBER_WITHDRAWN, key, message);
        }
        kafkaTemplate.send(KafkaTopics.USER_DELETED, key, message);
    }
}
