package com.fandom.feed.infra.redis;

import com.fandom.feed.application.policy.PostPolicy;
import com.fandom.feed.application.policy.ReactionSort;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.config.RedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@Import({PostListCacheService.class, RedisConfig.class, RedisAutoConfiguration.class})
class PostListCacheServiceIntegrationTest {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PostListCacheService postListCacheService;

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @AfterEach
    void tearDown() {
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("게시글 ID 목록 조회")
    class GetPostIds {
        @Test
        @DisplayName("cursor 없이 최신순 조회 - 전체 게시글 대상")
        void getPostIdsLatestWithoutCursor() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(ReactionSort.LATEST, null);

            // Then
            assertThat(result).containsExactly(post3, post2, post1);
        }

        @Test
        @DisplayName("cursor 없이 오래된순 조회 - 전체 게시글 대상")
        void getPostIdsOldestWithoutCursor() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(ReactionSort.OLDEST, null);

            // Then
            assertThat(result).containsExactly(post1, post2, post3);
        }

        @Test
        @DisplayName("cursor 포함 최신순 조회 - cursor 이전 게시글 대상")
        void getPostIdsLatestWithCursor() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(ReactionSort.LATEST, post3);

            // Then
            assertThat(result).containsExactly(post2, post1);
        }

        @Test
        @DisplayName("cursor 포함 오래된순 조회 - cursor 이후 게시글 대상")
        void getPostIdsOldestWithCursor() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(ReactionSort.OLDEST, post1);

            // Then
            assertThat(result).containsExactly(post2, post3);
        }

        @Test
        @DisplayName("캐시에 cursor 없음 - null 반환 (5페이지 초과)")
        void getPostIdsCursorNotInCache() {
            // Given
            UUID unknownCursor = UUID.randomUUID();

            // When
            List<UUID> result = postListCacheService.getPostIds(ReactionSort.LATEST, unknownCursor);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("캐시에 postId 추가")
    class AddPost {
        @Test
        @DisplayName("sort = LATEST - LATEST 키에만 저장")
        void addPostLatest() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPost(postId, ReactionSort.LATEST);

            // Then
            Double latestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_LATEST, postId.toString());
            Double oldestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_OLDEST, postId.toString());

            assertThat(latestScore).isNotNull();
            assertThat(oldestScore).isNull();
        }

        @Test
        @DisplayName("sort = OLDEST - OLDEST 키에만 저장")
        void addPostOldest() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPost(postId, ReactionSort.OLDEST);

            // Then
            Double latestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_LATEST, postId.toString());
            Double oldestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_OLDEST, postId.toString());

            assertThat(latestScore).isNull();
            assertThat(oldestScore).isNotNull();
        }

        @Test
        @DisplayName("sort = LATEST 일 때 MAX_CACHE_SIZE 초과 - 가장 오래된 게시글 ID 제거")
        void addPostLatestWhenExceedsMaxSize() {
            // Given
            UUID oldestPost = UUID.randomUUID();
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, oldestPost.toString(), 1);

            for (int i = 2; i <= PostPolicy.MAX_CACHE_SIZE; i++)
                redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_LATEST, UUID.randomUUID().toString(), i);

            // When
            postListCacheService.addPost(UUID.randomUUID(), ReactionSort.LATEST);

            // Then
            Long size = redisTemplate.opsForZSet().size(RedisKeyPrefix.POST_LIST_LATEST);
            Double oldestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_LATEST, oldestPost.toString());

            assertThat(size).isEqualTo(PostPolicy.MAX_CACHE_SIZE);
            assertThat(oldestScore).isNull();
        }

        @Test
        @DisplayName("sort = OLDEST 일 때 MAX_CACHE_SIZE 초과 - 가장 최신 게시글 ID 제거")
        void addPostOldestWhenExceedsMaxSize() {
            // Given
            UUID newestPost = UUID.randomUUID();
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, newestPost.toString(), Long.MAX_VALUE);

            for (int i = 1; i <= PostPolicy.MAX_CACHE_SIZE - 1; i++)
                redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_OLDEST, UUID.randomUUID().toString(), i);

            // When
            postListCacheService.addPost(UUID.randomUUID(), ReactionSort.OLDEST);

            // Then
            Long size = redisTemplate.opsForZSet().size(RedisKeyPrefix.POST_LIST_OLDEST);
            Double newestScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_OLDEST, newestPost.toString());

            assertThat(size).isEqualTo(PostPolicy.MAX_CACHE_SIZE);
            assertThat(newestScore).isNull();
        }
    }
}