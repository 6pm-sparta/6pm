package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.follow.application.port.FollowEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FollowEventKafkaPublisher implements FollowEventPublisher {

    private final KafkaTemplate<String, FollowEventMessage> kafkaTemplate;

    @Override
    public void publishFollowed(UUID followId, UUID followerId, UUID followeeId) {
        publish(KafkaTopics.USER_FOLLOWED, followId, followerId, followeeId);
    }

    @Override
    public void publishUnfollowed(UUID followId, UUID followerId, UUID followeeId) {
        publish(KafkaTopics.USER_UNFOLLOWED, followId, followerId, followeeId);
    }

    private void publish(String topic, UUID followId, UUID followerId, UUID followeeId) {
        kafkaTemplate.send(
                topic,
                followId.toString(),
                new FollowEventMessage(followId, followerId, followeeId)
        );
    }
}
