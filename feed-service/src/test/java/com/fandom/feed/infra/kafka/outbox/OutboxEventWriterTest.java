package com.fandom.feed.infra.kafka.outbox;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {
    @Mock
    OutboxEventRepository outboxEventRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    OutboxEventWriter outboxEventWriter;
    @Test
    @DisplayName("정상 동작 - 페이로드를 JSON으로 직렬화해 OutboxEvent를 저장한다")
    void write() throws JsonProcessingException {
        // given
        UUID aggregateId = UUID.randomUUID();
        Event.PostCreated payload = new Event.PostCreated(aggregateId, UUID.randomUUID(), "닉네임");
        String json = "{\"postId\":\"...\"}";

        given(objectMapper.writeValueAsString(payload)).willReturn(json);

        // when
        outboxEventWriter.write(aggregateId, OutboxEventType.POST_CREATED, payload);

        // then
        verify(outboxEventRepository).save(argThat(event ->
                event.getAggregateId().equals(aggregateId)
                        && event.getEventType() == OutboxEventType.POST_CREATED
                        && event.getPayload().equals(json)
        ));
    }

    @Test
    @DisplayName("JSON 직렬화 실패 - CustomException을 던지고 저장을 시도하지 않는다")
    void writeThrowsOnSerializationFailure() throws JsonProcessingException {
        // given
        UUID aggregateId = UUID.randomUUID();
        Object payload = new Object();

        given(objectMapper.writeValueAsString(payload)).willThrow(new JsonProcessingException("직렬화 실패") {});

        // when & then
        assertThrows(CustomException.class, () -> outboxEventWriter.write(aggregateId, OutboxEventType.POST_CREATED, payload));
        verify(outboxEventRepository, never()).save(any());
    }
}