package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.follow.application.port.FollowEventPublisher;
import com.fandom.user_service.member.infrastructure.kafka.outbox.application.OutboxAppender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FollowEventPublisher(port) 구현체. 발행은 Transactional Outbox를 통한다 —
 * Kafka로 직접 보내지 않고, 도메인 트랜잭션과 같은 트랜잭션에서 Outbox에 적재한다(OutboxAppender).
 * 실제 Kafka 발행은 OutboxPublisher 폴링이 수행한다. (port 시그니처는 유지 — 도메인은 발행 방식을 모른다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowEventKafkaPublisher implements FollowEventPublisher {

    private final OutboxAppender outboxAppender;

    @Override
    public void publishFollowed(UUID followId, UUID followerId, UUID followeeId, String nickname) {
        publish(KafkaTopics.USER_FOLLOWED, followId, followerId, followeeId, nickname);
    }

    @Override
    public void publishUnfollowed(UUID followId, UUID followerId, UUID followeeId) {
        publish(KafkaTopics.USER_UNFOLLOWED, followId, followerId, followeeId, null);
    }

    private void publish(String topic, UUID followId, UUID followerId, UUID followeeId, String nickname) {
        FollowEventMessage message = new FollowEventMessage(followId, followerId, followeeId, nickname);
        // aggregateId = followId (follow aggregate 순서 보장 단위)
        outboxAppender.append(topic, followId, message);
    }
}
