package com.fandom.user_service.member.infrastructure.kafka.outbox.application;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutbox;
import com.fandom.user_service.member.infrastructure.kafka.outbox.domain.UserOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 발행할 이벤트를 Outbox에 적재한다. 반드시 도메인 상태 변경 Writer의 @Transactional 메서드 안에서
 * (publisher를 통해) 호출되어야 상태 변경과 같은 트랜잭션으로 커밋된다.
 *
 * 이 서비스는 이벤트별 publisher(port 구현체)가 토픽/메시지 조립을 이미 담당하므로, Appender는
 * topic/aggregateId/payload만 받는 범용 append 한 메서드만 둔다(order-service는 Appender가
 * 이벤트별 메서드를 갖지만, 여기서는 DIP를 위해 기존 publisher port를 유지하는 구조라 역할을 나눈다).
 *
 * 직렬화 실패는 던진다 — 발행 불가능한 이벤트면 트랜잭션을 롤백시켜 상태/이벤트 불일치를 막는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final UserOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param topic       발행 토픽
     * @param aggregateId Kafka partition key (순서 보장 단위 — userId 또는 followId)
     * @param event       직렬화할 이벤트 메시지 객체
     */
    public void append(String topic, UUID aggregateId, Object event) {
        outboxRepository.save(
                UserOutbox.builder()
                        .aggregateId(aggregateId)
                        .topic(topic)
                        .payload(serialize(event))
                        .build());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
