package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.CreatorCreatedEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorCreatedKafkaPublisher implements CreatorCreatedEventPublisher {

    private final KafkaTemplate<String, CreatorCreatedMessage> kafkaTemplate;

    @Override
    public void publish(UUID userId, String nickname) {
        String key = userId.toString();
        CreatorCreatedMessage message = new CreatorCreatedMessage(userId, nickname);

        kafkaTemplate.send(KafkaTopics.USER_CREATOR_CREATED, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[UserEvent] Kafka event publish failed. topic={}, key={}, payload={}",
                                KafkaTopics.USER_CREATOR_CREATED, key, message, ex);
                    }
                });
    }
}
