package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "cache.ttl.post-list=300")
@Import(PostListCacheService.class)
class PostListCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PostListCacheService postListCacheService;

    @AfterEach
    void tearDown() {
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("게시글 ID 목록 조회")
    class GetPostIds {
        private final UUID authorId = UUID.randomUUID();
        private final String authorKey = RedisKeyPrefix.POST_LIST + authorId;

        @Test
        @DisplayName("cursor와 authorId 없음 - 전체 게시글 대상")
        void getPostIdsWithoutCursorAndAuthorId() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(null, null);

            // Then
            assertThat(result).containsExactly(post3, post2, post1);
        }

        @Test
        @DisplayName("authorId만 포함 - 작성자 게시글 대상")
        void getPostIdsWithAuthorId() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(authorKey, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(authorKey, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST + UUID.randomUUID(), post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(authorId, null);

            // Then
            assertThat(result).containsExactly(post2, post1);
        }

        @Test
        @DisplayName("cursor만 포함 - cursor 이전 전체 게시글 대상")
        void getPostIdsWithCursor() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, post3.toString(), 3000);

            // When
            List<UUID> result = postListCacheService.getPostIds(null, post3);

            // Then
            assertThat(result).containsExactly(post2, post1);
        }

        @Test
        @DisplayName("cursor와 authorId 포함 - cursor 이전 작성자 게시글 대상")
        void getPostIdsWithCursorAndAuthorId() {
            // Given
            UUID post1 = UUID.randomUUID();
            UUID post2 = UUID.randomUUID();
            UUID post3 = UUID.randomUUID();
            UUID post4 = UUID.randomUUID();

            redisTemplate.opsForZSet().add(authorKey, post1.toString(), 1000);
            redisTemplate.opsForZSet().add(authorKey, post2.toString(), 2000);
            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST + UUID.randomUUID(), post3.toString(), 3000);
            redisTemplate.opsForZSet().add(authorKey, post4.toString(), 4000);

            // When
            List<UUID> result = postListCacheService.getPostIds(authorId, post4);

            // Then
            assertThat(result).containsExactly(post2, post1);
        }

        @Test
        @DisplayName("캐시에 cursor 없음 - null 반환 (5페이지 초과)")
        void getPostIdsCursorNotInCache() {
            // Given
            UUID unknownCursor = UUID.randomUUID();

            // When
            List<UUID> result = postListCacheService.getPostIds(null, unknownCursor);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("캐시에 게시글 ID 추가")
    class AddPost {
        private final UUID authorId = UUID.randomUUID();
        private final String authorKey = RedisKeyPrefix.POST_LIST + authorId;

        @Test
        @DisplayName("성공 - 두 키에 모두 저장")
        void addPost() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPost(postId, authorId);

            // Then
            Double allScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_ALL, postId.toString());
            Double authorScore = redisTemplate.opsForZSet().score(authorKey, postId.toString());

            assertThat(allScore).isNotNull();
            assertThat(authorScore).isNotNull();
        }

        @Test
        @DisplayName("MAX_CACHE_SIZE 초과 - 가장 오래된 게시글 ID 제거")
        void addPostWhenExceedsMaxSize() {
            // Given
            UUID oldestPost = UUID.randomUUID();

            redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, oldestPost.toString(), 1);
            redisTemplate.opsForZSet().add(authorKey, oldestPost.toString(), 1);

            for (int i = 2; i <= FeedPolicy.MAX_CACHE_SIZE; i++) {
                redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, UUID.randomUUID().toString(), i);
                redisTemplate.opsForZSet().add(authorKey, UUID.randomUUID().toString(), i);
            }

            // When
            postListCacheService.addPost(UUID.randomUUID(), authorId);

            // Then
            Long allSize = redisTemplate.opsForZSet().size(RedisKeyPrefix.POST_LIST_ALL);
            Long authorSize = redisTemplate.opsForZSet().size(authorKey);

            assertThat(allSize).isEqualTo(FeedPolicy.MAX_CACHE_SIZE);
            assertThat(authorSize).isEqualTo(FeedPolicy.MAX_CACHE_SIZE);
        }
    }

    @Nested
    @DisplayName("캐시에 게시글 ID 목록 추가")
    class AddPostsForWarm {
        private final UUID authorId = UUID.randomUUID();
        private final String authorKey = RedisKeyPrefix.POST_LIST + authorId;

        @Test
        @DisplayName("authorId 없음 - all 키에만 저장")
        void addPostsForWarmWithoutAuthorId() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPostsForWarm(List.of(postId), null);

            // Then
            Double allScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_ALL, postId.toString());
            Double authorScore = redisTemplate.opsForZSet().score(authorKey, postId.toString());

            assertThat(allScore).isNotNull();
            assertThat(authorScore).isNull();
        }

        @Test
        @DisplayName("authorId 있음 - author 키에만 저장")
        void addPostsForWarmWithAuthorId() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPostsForWarm(List.of(postId), authorId);

            // Then
            Double allScore = redisTemplate.opsForZSet().score(RedisKeyPrefix.POST_LIST_ALL, postId.toString());
            Double authorScore = redisTemplate.opsForZSet().score(authorKey, postId.toString());

            assertThat(allScore).isNull();
            assertThat(authorScore).isNotNull();
        }
    }

    @Test
    @DisplayName("캐시에서 작성자 ID의 모든 게시글 ID 제거")
    void removeAllByAuthorId() {
        // given
        UUID authorId = UUID.randomUUID();
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();

        redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, postId1.toString(), 1);
        redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST_ALL, postId2.toString(), 2);
        redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST + authorId, postId1.toString(), 1);
        redisTemplate.opsForZSet().add(RedisKeyPrefix.POST_LIST + authorId, postId2.toString(), 2);

        // when
        postListCacheService.removeAllByAuthorId(List.of(postId1, postId2), authorId);

        // then
        assertThat(redisTemplate.opsForZSet().size(RedisKeyPrefix.POST_LIST_ALL)).isZero();
        assertThat(redisTemplate.opsForZSet().size(RedisKeyPrefix.POST_LIST + authorId)).isZero();
    }
}