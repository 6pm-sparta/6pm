package com.fandom.feed.infra.scheduler;

import com.fandom.feed.application.event.Event;
import com.fandom.feed.application.event.PostBroadcastHandler;
import com.fandom.feed.infra.kafka.outbox.OutboxEvent;
import com.fandom.feed.infra.kafka.outbox.OutboxEventRepository;
import com.fandom.feed.infra.kafka.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {
    private final OutboxEventRepository outboxEventRepository;
    private final PostBroadcastHandler postBroadcastHandler;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "#{${scheduler.outbox-publish.fixed-delay} * 1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                Event.PostCreated payload = objectMapper.readValue(event.getPayload(), Event.PostCreated.class);
                postBroadcastHandler.handle(payload.postId(), payload.authorId(), payload.nickname());
                event.markPublished();
            } catch (Exception e) {
                log.error("Outbox 이벤트 처리 실패 - eventId={}, aggregateId={}", event.getId(), event.getAggregateId(), e);
                event.markFailed();
            }
            outboxEventRepository.save(event);
        }
    }
}