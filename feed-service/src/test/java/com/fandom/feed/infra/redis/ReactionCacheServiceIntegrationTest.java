package com.fandom.feed.infra.redis;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.exception.LikeErrorCode;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.config.RedisConfig;
import com.fandom.feed.infra.redis.dto.PostCache;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(SpringExtension.class)
@Import({ReactionCacheService.class, RedisConfig.class, RedisAutoConfiguration.class})
public class ReactionCacheServiceIntegrationTest {
    @MockitoBean
    private LikeRepository likeRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ReactionCacheService reactionCacheService;

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
            List<PostCache.ReactionInfo> results = reactionCacheService.getReactionInfoBatch(
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
            List<PostCache.ReactionInfo> results = reactionCacheService.getReactionInfoBatch(
                    List.of(postId), null, false
            );

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().commentCount()).isEqualTo(2);
            assertThat(results.getFirst().likeCount()).isEqualTo(1);
            assertThat(results.getFirst().liked()).isFalse();
        }

        @Test
        @DisplayName("isLiked = true - liked 항상 true")
        void getReactionInfoBatchWithIsLiked() {
            // Given
            UUID postId = UUID.randomUUID();
            redisTemplate.opsForValue().set(RedisKeyPrefix.COMMENT_COUNT + postId, "2");
            redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId, "someone");

            // When
            List<PostCache.ReactionInfo> results = reactionCacheService.getReactionInfoBatch(
                    List.of(postId), null, true
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

    @Nested
    @DisplayName("캐시에 userId 삭제")
    class RemoveLike {
        @Test
        @DisplayName("userId 삭제 후 좋아요 수 반환")
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
        @DisplayName("캐시 미스 - DB에서 복구")
        void removeLikeCacheMiss() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(likeRepository.findAllByPostId(postId))
                    .thenReturn(List.of(Like.builder().postId(postId).userId(userId).build()));

            // When
            long count = reactionCacheService.removeLike(postId, UUID.randomUUID());

            // Then
            assertThat(count).isEqualTo(1);
            assertThat(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + postId, userId.toString())).isTrue();
        }
    }
}