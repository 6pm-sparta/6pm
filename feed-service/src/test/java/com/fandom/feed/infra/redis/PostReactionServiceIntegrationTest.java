package com.fandom.feed.infra.redis;

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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static com.fandom.feed.infra.redis.config.RedisKeyPrefix.COMMENT_COUNT;
import static com.fandom.feed.infra.redis.config.RedisKeyPrefix.LIKE_SET;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@Import({PostReactionService.class, RedisConfig.class, RedisAutoConfiguration.class})
public class PostReactionServiceIntegrationTest {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PostReactionService postReactionService;

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
        redisTemplate.getConnectionFactory().getConnection().serverCommands();
    }

    @Nested
    @DisplayName("리액션 정보 배치 조회")
    class GetReactionInfoBatch {
        @Test
        @DisplayName("userId 있음 - commentCount, likeCount, isLiked 정상 매핑")
        void getReactionInfoBatchWithUserId() {
            // Given
            UUID postId1 = UUID.randomUUID();
            UUID postId2 = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            redisTemplate.opsForValue().set(COMMENT_COUNT + postId1, "3");
            redisTemplate.opsForValue().set(COMMENT_COUNT + postId2, "5");

            redisTemplate.opsForSet().add(LIKE_SET + postId1, userId.toString(), "other");
            redisTemplate.opsForSet().add(LIKE_SET + postId2, "other");

            // When
            List<PostCache.ReactionInfo> results = postReactionService.getReactionInfoBatch(List.of(postId1, postId2), userId);

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
        @DisplayName("userId 없음 - isLiked 항상 false")
        void getReactionInfoBatch_withoutUserId() {
            // Given
            UUID postId = UUID.randomUUID();
            redisTemplate.opsForValue().set(COMMENT_COUNT + postId, "2");
            redisTemplate.opsForSet().add(LIKE_SET + postId, "someone");

            // When
            List<PostCache.ReactionInfo> results = postReactionService.getReactionInfoBatch(List.of(postId), null);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().commentCount()).isEqualTo(2);
            assertThat(results.getFirst().likeCount()).isEqualTo(1);
            assertThat(results.getFirst().liked()).isFalse();
        }

        @Test
        @DisplayName("Redis에 데이터 없음 - 기본값 0, false 반환")
        void getReactionInfoBatch_noData() {
            // Given
            UUID postId = UUID.randomUUID();

            // When
            List<PostCache.ReactionInfo> results = postReactionService.getReactionInfoBatch(List.of(postId), UUID.randomUUID());

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().commentCount()).isZero();
            assertThat(results.getFirst().likeCount()).isZero();
            assertThat(results.getFirst().liked()).isFalse();
        }
    }
}