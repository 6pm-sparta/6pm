package com.fandom.feed.infra.outbox;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.util.LogContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static java.util.Map.entry;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    public void write(UUID aggregateId, OutboxEventType eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.of(aggregateId, eventType, json));
        } catch (JsonProcessingException e) {
            LogContext.error(e, "[OutboxEvent] 직렬화 실패", entry("aggregateId", aggregateId), entry("eventType", eventType));
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            LogContext.error(e, "[OutboxEvent] 저장 실패", entry("aggregateId", aggregateId), entry("eventType", eventType));
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void writeAll(List<UUID> aggregateIds, OutboxEventType eventType, Function<UUID, Object> payloadFactory) {
        try {
            List<OutboxEvent> events = new ArrayList<>();
            for (UUID aggregateId : aggregateIds) {
                String json = objectMapper.writeValueAsString(payloadFactory.apply(aggregateId));
                events.add(OutboxEvent.of(aggregateId, eventType, json));
            }
            outboxEventRepository.saveAll(events);
        } catch (JsonProcessingException e) {
            LogContext.error(e, "[OutboxEvent] 일괄 직렬화 실패", entry("aggregateIds", aggregateIds), entry("eventType", eventType));
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            LogContext.error(e, "[OutboxEvent] 일괄 저장 실패", entry("aggregateIds", aggregateIds), entry("eventType", eventType));
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}