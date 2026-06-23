package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.CreatorCreatedEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreatorCreatedKafkaPublisher implements CreatorCreatedEventPublisher {

    private final KafkaTemplate<String, CreatorCreatedMessage> kafkaTemplate;

    @Override
    public void publish(UUID userId, String nickname) {
        kafkaTemplate.send(
                KafkaTopics.USER_CREATOR_CREATED,
                userId.toString(),
                new CreatorCreatedMessage(userId, nickname)
        );
    }
}
