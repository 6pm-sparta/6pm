package com.fandom.order_service.kafka.outbox.application;

import com.fandom.order_service.kafka.outbox.domain.OrderOutbox;
import com.fandom.order_service.kafka.outbox.domain.OrderOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Outbox 레코드 한 건을 레코드 단위 트랜잭션으로 발행한다(한 건 실패가 다른 레코드에 전이되지 않게
 * OutboxPublisher와 빈 분리 — self-invocation은 @Transactional 프록시를 안 타므로).
 *
 * .get()으로 브로커 ack까지 동기로 기다려 발행 성공을 확인한 뒤에만 PUBLISHED로 전이한다.
 * 저장된 payload는 JsonNode로 되돌려 보내 직렬화 형태(@JsonProperty 매핑 포함)를 보존한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRecordPublisher {

    private final OrderOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishOne(UUID outboxId) {
        OrderOutbox record = outboxRepository.findById(outboxId).orElse(null);
        if (record == null) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(record.getPayload());
            kafkaTemplate.send(record.getTopic(), record.getAggregateId().toString(), payload).get();
            record.markPublished();
            log.info("[Outbox 발행 성공] topic={}, aggregateId={}, outboxId={}",
                    record.getTopic(), record.getAggregateId(), record.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Outbox 발행 중단] outboxId={}", record.getId(), e);
        } catch (Exception e) {
            // markPublished() 미호출 → status는 PENDING 유지, 다음 폴링이 재시도.
            log.error("[Outbox 발행 실패] topic={}, outboxId={} — 다음 폴링에서 재시도",
                    record.getTopic(), record.getId(), e);
        }
    }
}
