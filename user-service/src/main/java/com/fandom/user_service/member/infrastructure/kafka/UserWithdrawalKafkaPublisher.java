package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.MemberWithdrawalEventPublisher;
import com.fandom.user_service.member.domain.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawalKafkaPublisher implements MemberWithdrawalEventPublisher {

    private final KafkaTemplate<String, UserWithdrawalMessage> kafkaTemplate;

    @Override
    public void publish(UUID userId, Role role) {
        UserWithdrawalMessage message = new UserWithdrawalMessage(userId);
        String key = userId.toString();

        if (role == Role.CREATOR) {
            send(KafkaTopics.USER_CREATOR_WITHDRAWN, key, message);
        } else if (role == Role.MEMBER) {
            send(KafkaTopics.USER_MEMBER_WITHDRAWN, key, message);
        }
        send(KafkaTopics.USER_DELETED, key, message);
    }

    private void send(String topic, String key, UserWithdrawalMessage message) {
        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[UserEvent] Kafka event publish failed. topic={}, key={}, payload={}",
                                topic, key, message, ex);
                    }
                });
    }
}
