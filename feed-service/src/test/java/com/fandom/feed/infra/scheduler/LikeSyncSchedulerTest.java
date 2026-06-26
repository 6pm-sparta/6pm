package com.fandom.feed.infra.scheduler;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeSyncSchedulerTest {
    @Mock
    LikeRepository likeRepository;

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    SetOperations<String, String> setOperations;

    @InjectMocks
    LikeSyncScheduler scheduler;

    @Test
    @DisplayName("좋아요 동기화 스케줄러 정상 실행")
    void schedulerRuns() {
        assertDoesNotThrow(() -> scheduler.syncLikes());
    }

    @Test
    @DisplayName("Redis에만 있는 좋아요 - DB에 insert")
    void syncLikesInsert() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userInDb = UUID.randomUUID();
        UUID userOnlyInRedis = UUID.randomUUID();

        Like existing = Like.builder().postId(postId).userId(userInDb).build();
        when(likeRepository.findAll()).thenReturn(List.of(existing));

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKeyPrefix.LIKE_SET + postId))
                .thenReturn(Set.of(userInDb.toString(), userOnlyInRedis.toString()));

        // When
        scheduler.syncLikes();

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Like>> captor = ArgumentCaptor.forClass(List.class);
        
        verify(likeRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(Like::getUserId).containsExactly(userOnlyInRedis);
    }

    @Test
    @DisplayName("Redis가 비어있음 - insert하지 않음")
    void syncLikesWhenRedisEmpty() {
        // Given
        UUID postId = UUID.randomUUID();
        Like existing = Like.builder().postId(postId).userId(UUID.randomUUID()).build();
        when(likeRepository.findAll()).thenReturn(List.of(existing));

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKeyPrefix.LIKE_SET + postId)).thenReturn(Collections.emptySet());

        // When
        scheduler.syncLikes();

        // Then
        verify(likeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Redis와 DB 동일 - insert하지 않음")
    void syncLikesNoChange() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Like existing = Like.builder().postId(postId).userId(userId).build();
        when(likeRepository.findAll()).thenReturn(List.of(existing));

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKeyPrefix.LIKE_SET + postId)).thenReturn(Set.of(userId.toString()));

        // When
        scheduler.syncLikes();

        // Then
        verify(likeRepository, never()).saveAll(any());
    }
}