package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.follow.application.port.FollowEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowEventKafkaPublisher implements FollowEventPublisher {

    private final KafkaTemplate<String, FollowEventMessage> kafkaTemplate;

    @Override
    public void publishFollowed(UUID followId, UUID followerId, UUID followeeId, String nickname) {
        publish(KafkaTopics.USER_FOLLOWED, followId, followerId, followeeId, nickname);
    }

    @Override
    public void publishUnfollowed(UUID followId, UUID followerId, UUID followeeId) {
        publish(KafkaTopics.USER_UNFOLLOWED, followId, followerId, followeeId, null);
    }

    private void publish(String topic, UUID followId, UUID followerId, UUID followeeId, String nickname) {
        String key = followId.toString();
        FollowEventMessage message = new FollowEventMessage(followId, followerId, followeeId, nickname);

        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[UserEvent] Kafka event publish failed. topic={}, key={}, payload={}",
                                topic, key, message, ex);
                    }
                });
    }
}
