package com.fandom.feed.infra.redis;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.LikeErrorCode;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "cache.ttl.comment-count=300")
@Import(ReactionCacheService.class)
public class ReactionCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @MockitoBean
    private PostReader postReader;

    @MockitoBean
    private LikeRepository likeRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ReactionCacheService reactionCacheService;

    @AfterEach
    void tearDown() {
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("리액션 정보 배치 조회")
    class GetReactionInfoBatch {
        @Test
        @DisplayName("userId 있음 - commentCount, likeCount, liked 정상 매핑")
        void getReactionInfoBatchWithUserId() {
            // Given
            UUID postId1 = UUID.randomUUID();
            UUID postId2 = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            redisTemplate.opsForValue().set(RedisKeyPrefix.COMMENT_COUNT + postId1, "3");
            redisTemplate.opsForValue().set(RedisKeyPrefix.COMMENT_COUNT + postId2, "5");

            redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId1, userId.toString(), "other");
            redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId2, "other");

            // When
            List<ReactionInfoCache> results = reactionCacheService.getReactionInfoBatch(
                    List.of(postId1, postId2), userId, false
            );

            // Then
            assertThat(results).hasSize(2);

            assertThat(results.getFirst().commentCount()).isEqualTo(3);
            assertThat(results.getFirst().likeCount()).isEqualTo(2);
            assertThat(results.getFirst().liked()).isTrue();

            assertThat(results.get(1).commentCount()).isEqualTo(5);
            assertThat(results.get(1).likeCount()).isEqualTo(1);
            assertThat(results.get(1).liked()).isFalse();
        }

        @Test
        @DisplayName("userId 없음 - liked 항상 false")
        void getReactionInfoBatchWithoutUserId() {
            // Given
            UUID postId = UUID.randomUUID();
            redisTemplate.opsForValue().set(RedisKeyPrefix.COMMENT_COUNT + postId, "2");
            redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId, "someone");

            // When
            List<ReactionInfoCache> results = reactionCacheService.getReactionInfoBatch(
                    List.of(postId), null, false
            );

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().commentCount()).isEqualTo(2);
            assertThat(results.getFirst().likeCount()).isEqualTo(1);
            assertThat(results.getFirst().liked()).isFalse();
        }

        @Test
        @DisplayName("isLiked = true - userId 있어도 liked 항상 true")
        void getReactionInfoBatchWithIsLiked() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            redisTemplate.opsForValue().set(RedisKeyPrefix.COMMENT_COUNT + postId, "2");
            redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId, "someone");

            // When
            List<ReactionInfoCache> results = reactionCacheService.getReactionInfoBatch(
                    List.of(postId), userId, true
            );

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().commentCount()).isEqualTo(2);
            assertThat(results.getFirst().likeCount()).isEqualTo(1);
            assertThat(results.getFirst().liked()).isTrue();
        }
    }

    @Nested
    @DisplayName("캐시에 userId 추가")
    class AddLike {
        @Test
        @DisplayName("좋아요 없음 - userId 추가 후 좋아요 수 반환")
        void addLikeNotInRedis() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            // When
            long count = reactionCacheService.addLike(postId, userId);

            // Then
            assertThat(count).isEqualTo(1);
            assertThat(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + postId, userId.toString())).isTrue();
        }

        @Test
        @DisplayName("좋아요 있음 - 예외 발생")
        void addLikeInRedis() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            reactionCacheService.addLike(postId, userId);

            // When & Then
            assertThatThrownBy(() -> reactionCacheService.addLike(postId, userId))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", LikeErrorCode.DUPLICATE_LIKE);
        }
    }

    @Test
    @DisplayName("캐시에서 userId 삭제 후 좋아요 수 반환")
    void removeLike() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        reactionCacheService.addLike(postId, userA);
        reactionCacheService.addLike(postId, userB);

        // When
        long count = reactionCacheService.removeLike(postId, userA);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 배치 조회 후 캐시 저장")
    void fetchFromDbAndCache() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID likeUserId = UUID.randomUUID();
        Post post = mock(Post.class);

        when(post.getId()).thenReturn(postId);
        when(post.getCommentCount()).thenReturn(5L);
        when(postReader.findAllByIds(List.of(postId))).thenReturn(List.of(post));
        when(likeRepository.findLikeUsersByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of(likeUserId)));

        // When
        List<ReactionInfoCache> results = reactionCacheService.getReactionInfoBatch(
                List.of(postId), null, false
        );

        // Then
        assertThat(results.getFirst().commentCount()).isEqualTo(5L);
        assertThat(results.getFirst().likeCount()).isEqualTo(1L);

        assertThat(redisTemplate.opsForValue().get(RedisKeyPrefix.COMMENT_COUNT + postId)).isEqualTo("5");
        assertThat(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + postId, likeUserId.toString())).isTrue();
    }
}