package com.fandom.feed.infra.kafka.outbox;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.event.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventWriter outboxEventWriter;

    @Nested
    @DisplayName("Outbox 저장")
    class Write {
        @Test
        @DisplayName("정상 동작 - payload를 JSON으로 직렬화해 OutboxEvent 저장")
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
        @DisplayName("JSON 직렬화 실패 - 예외 발생 후 OutboxEvent 저장 안 함")
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
}