package com.fandom.feed.infra.scheduler;

import com.fandom.feed.application.event.Event;
import com.fandom.feed.application.event.PostBroadcastHandler;
import com.fandom.feed.infra.kafka.outbox.OutboxEvent;
import com.fandom.feed.infra.kafka.outbox.OutboxEventRepository;
import com.fandom.feed.infra.kafka.outbox.OutboxEventType;
import com.fandom.feed.infra.kafka.outbox.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {
    @Mock
    OutboxEventRepository outboxEventRepository;

    @Mock
    PostBroadcastHandler postBroadcastHandler;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    OutboxPublisherScheduler outboxPublisherScheduler;

    @Test
    @DisplayName("정상 작동 - 역직렬화 후 핸들러를 호출하고 PUBLISHED로 마킹")
    void publishPendingEvents() throws Exception {
        // given
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String json = "{\"postId\":\"...\"}";

        OutboxEvent event = OutboxEvent.of(postId, OutboxEventType.POST_CREATED, json);
        Event.PostCreated payload = new Event.PostCreated(postId, authorId, "닉네임");

        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of(event));
        given(objectMapper.readValue(json, Event.PostCreated.class)).willReturn(payload);

        // when
        outboxPublisherScheduler.publishPendingEvents();

        // then
        verify(postBroadcastHandler).handle(postId, authorId, "닉네임");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("일시적 처리 실패 3회 미만 - 재시도를 위해 PENDING으로 유지")
    void publishPendingEventsFirstFailureRetainsPending() throws Exception {
        // given
        String invalidJson = "invalid";
        OutboxEvent event = OutboxEvent.of(UUID.randomUUID(), OutboxEventType.POST_CREATED, invalidJson);

        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of(event));
        given(objectMapper.readValue(invalidJson, Event.PostCreated.class)).willThrow(new JsonProcessingException("파싱 실패") {});

        // when
        assertDoesNotThrow(() -> outboxPublisherScheduler.publishPendingEvents());

        // then
        verify(postBroadcastHandler, never()).handle(any(), any(), any());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(0);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("일시적 처리 실패 3회 이상 - FAILED로 마킹되어 재시도 멈춤")
    void publishPendingEventsExhaustedRetries() throws Exception {
        // given
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String json = "{\"postId\":\"...\"}";

        OutboxEvent event = OutboxEvent.of(postId, OutboxEventType.POST_CREATED, json);
        Event.PostCreated payload = new Event.PostCreated(postId, authorId, "닉네임");

        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of(event));
        given(objectMapper.readValue(json, Event.PostCreated.class)).willReturn(payload);
        doThrow(new RuntimeException("User 서비스 장애")).when(postBroadcastHandler).handle(postId, authorId, "닉네임");

        // when
        outboxPublisherScheduler.publishPendingEvents();
        outboxPublisherScheduler.publishPendingEvents();
        outboxPublisherScheduler.publishPendingEvents();

        // then
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxEventRepository, times(3)).save(event);
    }

    @Test
    @DisplayName("JSON 역직렬화 실패 - 재시도 없이 즉시 FAILED로 마킹")
    void publishPendingEventsDeserializationFailure() throws Exception {
        // given
        String invalidJson = "invalid";
        OutboxEvent event = OutboxEvent.of(UUID.randomUUID(), OutboxEventType.POST_CREATED, invalidJson);

        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of(event));
        given(objectMapper.readValue(invalidJson, Event.PostCreated.class)).willThrow(new JsonProcessingException("파싱 실패") {});

        // when
        assertDoesNotThrow(() -> outboxPublisherScheduler.publishPendingEvents());

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(0);
        verify(postBroadcastHandler, never()).handle(any(), any(), any());
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("PENDING 상태 이벤트 여러 개 - 모두 순서대로 처리되고 각각 저장")
    void publishPendingEventsMultiple() throws Exception {
        // given
        OutboxEvent event1 = OutboxEvent.of(UUID.randomUUID(), OutboxEventType.POST_CREATED, "{\"a\":1}");
        OutboxEvent event2 = OutboxEvent.of(UUID.randomUUID(), OutboxEventType.POST_CREATED, "{\"a\":2}");

        Event.PostCreated payload1 = new Event.PostCreated(UUID.randomUUID(), UUID.randomUUID(), "닉네임1");
        Event.PostCreated payload2 = new Event.PostCreated(UUID.randomUUID(), UUID.randomUUID(), "닉네임2");

        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of(event1, event2));
        given(objectMapper.readValue("{\"a\":1}", Event.PostCreated.class)).willReturn(payload1);
        given(objectMapper.readValue("{\"a\":2}", Event.PostCreated.class)).willReturn(payload2);

        // when
        outboxPublisherScheduler.publishPendingEvents();

        // then
        verify(postBroadcastHandler).handle(payload1.postId(), payload1.authorId(), payload1.nickname());
        verify(postBroadcastHandler).handle(payload2.postId(), payload2.authorId(), payload2.nickname());
        verify(outboxEventRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("PENDING 이벤트 없음 - 아무것도 처리하지 않음")
    void publishPendingEventsNoEvents() {
        // given
        given(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)).willReturn(List.of());

        // when
        outboxPublisherScheduler.publishPendingEvents();

        // then
        verify(postBroadcastHandler, never()).handle(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }
}