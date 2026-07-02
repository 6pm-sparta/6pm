package com.fandom.feed.infra.redis;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@TestPropertySource(properties = "cache.ttl.timeline=86400")
@Import(TimelineCacheService.class)
class TimelineCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    TimelineCacheService timelineCacheService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("캐시에 게시글 ID 추가")
    class AddPost {
        @Test
        @DisplayName("MAX_CACHE_SIZE 미만 - 워밍업된 사용자의 타임라인에 게시글 ID 추가")
        void addPostWhenUnderMaxSize() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            UUID userId3 = UUID.randomUUID();
            long score = 1000L;

            timelineCacheService.addPostsForWarm(userId1, List.of());
            timelineCacheService.addPostsForWarm(userId2, List.of());

            // when
            timelineCacheService.addPost(List.of(userId1, userId2, userId3), postId, score);

            // then
            Set<String> result1 = redisTemplate.opsForZSet().range(RedisKeyPrefix.TIMELINE + userId1, 0, -1);
            Set<String> result2 = redisTemplate.opsForZSet().range(RedisKeyPrefix.TIMELINE + userId2, 0, -1);

            assertThat(result1).contains(postId.toString());
            assertThat(result2).contains(postId.toString());
        }

        @Test
        @DisplayName("MAX_CACHE_SIZE 초과 - 가장 오래된 게시글 ID 제거")
        void addPostWhenExceedsMaxSize() {
            // given
            UUID userId = UUID.randomUUID();
            String key = RedisKeyPrefix.TIMELINE + userId;

            for (int i = 0; i < FeedPolicy.MAX_CACHE_SIZE; i++)
                redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), i);

            UUID newPostId = UUID.randomUUID();
            long newScore = FeedPolicy.MAX_CACHE_SIZE + 100;

            // when
            timelineCacheService.addPost(List.of(userId), newPostId, newScore);

            // then
            Long size = redisTemplate.opsForZSet().size(key);
            assertThat(size).isEqualTo(FeedPolicy.MAX_CACHE_SIZE);

            Set<String> latest = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
            assertThat(latest).containsExactly(newPostId.toString());
        }
    }

    @Nested
    @DisplayName("캐시에 게시글 ID 삭제")
    class RemovePost {
        @Test
        @DisplayName("정상 동작 - 여러 유저의 타임라인에서 게시글을 일괄 제거")
        void removePostMultipleUsers() {
            // given
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            long score = UuidV7TimestampExtractor.extract(postId);

            redisTemplate.opsForZSet().add(RedisKeyPrefix.TIMELINE + userId1, postId.toString(), score);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.TIMELINE + userId2, postId.toString(), score);

            // when
            timelineCacheService.removePost(List.of(userId1, userId2), postId);

            // then
            assertThat(redisTemplate.opsForZSet().size(RedisKeyPrefix.TIMELINE + userId1)).isEqualTo(0);
            assertThat(redisTemplate.opsForZSet().size(RedisKeyPrefix.TIMELINE + userId2)).isEqualTo(0);
        }

        @Test
        @DisplayName("타임라인에 없는 게시글 제거 - 예외 없이 정상 종료")
        void removePostNotExisting() {
            // given
            UUID userId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();

            // when & then
            assertDoesNotThrow(() -> timelineCacheService.removePost(List.of(userId), postId));
        }
    }
}