package com.fandom.user_service.member.infrastructure.kafka;

import com.fandom.user_service.member.application.port.CreatorCreatedEventPublisher;
import com.fandom.user_service.member.infrastructure.kafka.outbox.application.OutboxAppender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * CreatorCreatedEventPublisher(port) 구현체. 발행은 Transactional Outbox를 통한다.
 * 도메인 트랜잭션과 같은 트랜잭션에서 Outbox에 적재하고, 실제 Kafka 발행은 OutboxPublisher 폴링이 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorCreatedKafkaPublisher implements CreatorCreatedEventPublisher {

    private final OutboxAppender outboxAppender;

    @Override
    public void publish(UUID userId, String nickname) {
        CreatorCreatedMessage message = new CreatorCreatedMessage(userId, nickname);
        // aggregateId = userId
        outboxAppender.append(KafkaTopics.USER_CREATOR_CREATED, userId, message);
    }
}
