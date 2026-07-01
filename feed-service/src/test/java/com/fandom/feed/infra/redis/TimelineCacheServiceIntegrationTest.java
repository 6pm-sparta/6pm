package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TimelineCacheService.class)
class TimelineCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    TimelineCacheService timelineCacheService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Nested
    @DisplayName("캐시에 게시글 ID 추가")
    class AddPosts {
        @Test
        @DisplayName("MAX_CACHE_SIZE 미만 - 여러 사용자의 타임라인에 게시글 ID 추가")
        void addPostsWhenUnderMaxSize() {
            // given
            UUID postId = UUID.randomUUID();
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            long score = 1000L;

            // when
            timelineCacheService.addPosts(List.of(userId1, userId2), postId, score);

            // then
            Set<String> result1 = redisTemplate.opsForZSet().range(RedisKeyPrefix.TIMELINE + userId1, 0, -1);
            Set<String> result2 = redisTemplate.opsForZSet().range(RedisKeyPrefix.TIMELINE + userId2, 0, -1);

            assertThat(result1).containsExactly(postId.toString());
            assertThat(result2).containsExactly(postId.toString());
        }

        @Test
        @DisplayName("MAX_CACHE_SIZE 초과 - 가장 오래된 게시글 ID 제거")
        void addPostsWhenExceedsMaxSize() {
            // given
            UUID userId = UUID.randomUUID();
            String key = RedisKeyPrefix.TIMELINE + userId;

            for (int i = 0; i < FeedPolicy.MAX_CACHE_SIZE; i++)
                redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), i);

            UUID newPostId = UUID.randomUUID();
            long newScore = FeedPolicy.MAX_CACHE_SIZE + 100;

            // when
            timelineCacheService.addPosts(List.of(userId), newPostId, newScore);

            // then
            Long size = redisTemplate.opsForZSet().size(key);
            assertThat(size).isEqualTo(FeedPolicy.MAX_CACHE_SIZE);

            Set<String> latest = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
            assertThat(latest).containsExactly(newPostId.toString());
        }
    }
}