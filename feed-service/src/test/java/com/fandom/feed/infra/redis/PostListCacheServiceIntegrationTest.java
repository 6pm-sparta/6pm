package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.util.UuidV7Generator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "cache.ttl.post-list=300")
@Import(PostListCacheService.class)
class PostListCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    private StringRedisTemplate redisTemplate;

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
    @DisplayName("여러 작성자의 게시글 ID 배치 조회")
    class GetPostIdsBatch {
        private UUID authorId1;
        private UUID authorId2;
        private UUID authorId3;

        @BeforeEach
        void setUp() {
            authorId1 = UUID.randomUUID();
            authorId2 = UUID.randomUUID();
            authorId3 = UUID.randomUUID();
        }

        @Test
        @DisplayName("cursor 없음 - 각 작성자의 최신 게시글 목록 반환")
        void getPostIdsBatchWithoutCursor() {
            // given
            UUID postId1 = UUID.randomUUID();
            UUID postId2 = UUID.randomUUID();
            postListCacheService.addPostsForWarm(List.of(postId1), authorId1);
            postListCacheService.addPostsForWarm(List.of(postId2), authorId2);

            // when
            Map<UUID, List<UUID>> result = postListCacheService.getPostIdsBatch(List.of(authorId1, authorId2), null);

            // then
            assertThat(result.get(authorId1)).containsExactly(postId1);
            assertThat(result.get(authorId2)).containsExactly(postId2);
        }

        @Test
        @DisplayName("cursor 있음 - cursor 이전 게시글만 반환")
        void getPostIdsBatchWithCursor() throws InterruptedException {
            UuidV7Generator uuidV7Generator = new UuidV7Generator();

            // given
            UUID olderPostId = uuidV7Generator.generate();
            Thread.sleep(10);
            UUID newerPostId = uuidV7Generator.generate();
            postListCacheService.addPostsForWarm(List.of(olderPostId, newerPostId), authorId1);

            // when
            Map<UUID, List<UUID>> result = postListCacheService.getPostIdsBatch(List.of(authorId1), newerPostId);

            // then
            assertThat(result.get(authorId1)).containsExactly(olderPostId);
        }

        @Test
        @DisplayName("특정 작성자의 캐시가 없으면 빈 목록 반환")
        void getPostIdsBatchWithNoCacheForAuthor() {
            // given
            UUID postId = UUID.randomUUID();
            postListCacheService.addPostsForWarm(List.of(postId), authorId1);

            // when
            Map<UUID, List<UUID>> result = postListCacheService.getPostIdsBatch(List.of(authorId1, authorId2), null);

            // then
            assertThat(result.get(authorId1)).containsExactly(postId);
            assertThat(result.get(authorId2)).isEmpty();
        }

        @Test
        @DisplayName("cursor가 캐시에 없으면 5페이지 초과로 판단해 null 반환")
        void getPostIdsBatchReturnsNullWhenCursorNotFound() {
            // given
            UUID postId = UUID.randomUUID();
            postListCacheService.addPostsForWarm(List.of(postId), authorId1);
            UUID unknownCursor = UUID.randomUUID();

            // when
            Map<UUID, List<UUID>> result = postListCacheService.getPostIdsBatch(List.of(authorId1), unknownCursor);

            // then
            assertThat(result.get(authorId1)).isNull();
        }

        @Test
        @DisplayName("여러 작성자를 섞어 조회해도 각 작성자별로 독립적인 결과 반환")
        void getPostIdsBatchWithMixedResults() {
            // given
            UUID postId1 = UUID.randomUUID();
            postListCacheService.addPostsForWarm(List.of(postId1), authorId1);
            UUID postId3 = UUID.randomUUID();
            postListCacheService.addPostsForWarm(List.of(postId3), authorId3);

            // when
            Map<UUID, List<UUID>> result = postListCacheService.getPostIdsBatch(List.of(authorId1, authorId2, authorId3), null);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(authorId1)).containsExactly(postId1);
            assertThat(result.get(authorId2)).isEmpty();
            assertThat(result.get(authorId3)).containsExactly(postId3);
        }
    }

    @Nested
    @DisplayName("캐시 워밍업 완료 여부 확인")
    class IsCacheReady {
        private final UUID authorId = UUID.randomUUID();

        @Test
        @DisplayName("워밍업 마커 있음 - true")
        void isCacheReadyWhenWarmed() {
            // Given
            postListCacheService.addPostsForWarm(List.of(UUID.randomUUID()), authorId);

            // When
            boolean result = postListCacheService.isCacheReady(authorId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("워밍업 마커 없음 - false")
        void isCacheReadyWhenNotWarmed() {
            // When
            boolean result = postListCacheService.isCacheReady(authorId);

            // Then
            assertThat(result).isFalse();
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

        @Test
        @DisplayName("워밍업 완료 마커가 함께 저장")
        void addPostsForWarmAddsWarmedMarker() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPostsForWarm(List.of(postId), authorId);

            // Then
            Double markerScore = redisTemplate.opsForZSet().score(authorKey, FeedPolicy.WARMED_MARKER);
            assertThat(markerScore).isEqualTo(-1);
        }

        @Test
        @DisplayName("TTL 설정")
        void addPostsForWarmSetsExpire() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            postListCacheService.addPostsForWarm(List.of(postId), authorId);

            // Then
            Long ttl = redisTemplate.getExpire(authorKey);
            assertThat(ttl).isPositive();
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