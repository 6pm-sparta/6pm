package com.fandom.feed.infra.scheduler;

import com.fandom.feed.application.event.Event;
import com.fandom.feed.application.event.PostBroadcastHandler;
import com.fandom.feed.infra.outbox.OutboxEvent;
import com.fandom.feed.infra.outbox.OutboxEventRepository;
import com.fandom.feed.infra.outbox.OutboxStatus;
import com.fandom.feed.infra.util.LogContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.fandom.feed.infra.util.LogContext.entry;

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
                switch (event.getEventType()) {
                    case POST_CREATED -> {
                        Event.PostCreated payload = objectMapper.readValue(event.getPayload(), Event.PostCreated.class);
                        postBroadcastHandler.handlePostCreated(payload.postId(), payload.authorId(), payload.nickname());
                    }
                    case POST_DELETED -> {
                        Event.PostDeleted payload = objectMapper.readValue(event.getPayload(), Event.PostDeleted.class);
                        postBroadcastHandler.handlePostDeleted(payload.postId(), payload.authorId());
                    }
                }
                event.markPublished();
            } catch (JsonProcessingException e) {
                LogContext.error(e, "[OutboxEvent] 역직렬화 실패",
                        entry("eventId", event.getId()),
                        entry("aggregateId", event.getAggregateId())
                );
                event.markFailedImmediately();
            } catch (Exception e) {
                LogContext.error(e, "[OutboxEvent] 처리 실패",
                        entry("eventId", event.getId()),
                        entry("aggregateId", event.getAggregateId())
                );
                event.markFailed();
            }
            outboxEventRepository.save(event);
        }
    }
}