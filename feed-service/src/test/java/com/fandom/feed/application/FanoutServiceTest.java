package com.fandom.feed.application;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.infra.redis.TimelineCacheService;
import com.fasterxml.uuid.Generators;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class FanoutServiceTest {
    @Mock
    TimelineCacheService timelineCacheService;

    @InjectMocks
    FanoutService fanoutService;

    @Nested
    @DisplayName("팔로워 청크로 타임라인 추가 요청")
    class InsertChunk {
        @Test
        @DisplayName("성공 - UUIDv7 타임스탬프를 score로 추출해 타임라인 추가 요청")
        void insertChunk() {
            // given
            UUID postId = Generators.timeBasedEpochGenerator().generate();
            UUID cursor = UUID.randomUUID();
            List<UUID> followerChunk = List.of(UUID.randomUUID(), UUID.randomUUID());
            long expectedScore = UuidV7TimestampExtractor.extract(postId);

            // when
            fanoutService.insertChunk(postId, cursor, followerChunk);

            // then
            verify(timelineCacheService).addPosts(followerChunk, postId, expectedScore);
        }

        @Test
        @DisplayName("실패 - 예외를 호출자에게 전파하지 않음")
        void insertChunkSwallowsException() {
            // given
            UUID postId = Generators.timeBasedEpochGenerator().generate();
            UUID cursor = UUID.randomUUID();
            List<UUID> followerChunk = List.of(UUID.randomUUID());

            doThrow(new RuntimeException("Redis 연결 실패")).when(timelineCacheService).addPosts(any(), any(), anyLong());

            // when & then
            assertDoesNotThrow(() -> fanoutService.insertChunk(postId, cursor, followerChunk));
        }
    }
}