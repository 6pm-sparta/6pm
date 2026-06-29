package com.fandom.feed.application.event;

import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(SpringExtension.class)
@Import({CommentCountEventListener.class, RedisConfig.class, RedisAutoConfiguration.class})
class CommentCountEventListenerIntegrationTest {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CommentCountEventListener listener;

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("댓글 생성 이벤트 발생 - Redis 댓글 수 증가")
    void handleCommentCreated() {
        // given
        UUID postId = UUID.randomUUID();
        String key = RedisKeyPrefix.COMMENT_COUNT + postId;

        // when
        listener.handleCommentCreated(new Event.CommentCreated(postId));

        // then
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("1");
    }

    @Test
    @DisplayName("댓글 삭제 이벤트 발생 - Redis 댓글 수 감소")
    void handleCommentDeleted() {
        // given
        UUID postId = UUID.randomUUID();
        String key = RedisKeyPrefix.COMMENT_COUNT + postId;
        redisTemplate.opsForValue().set(key, "2");

        // when
        listener.handleCommentDeleted(new Event.CommentDeleted(postId));

        // then
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("1");
    }

    @Test
    @DisplayName("댓글 수 0일 때 삭제 이벤트 발생 - 0 미만으로 내려가지 않음")
    void handleCommentDeleted_minZero() {
        // given
        UUID postId = UUID.randomUUID();
        String key = RedisKeyPrefix.COMMENT_COUNT + postId;
        redisTemplate.opsForValue().set(key, "0");

        // when
        listener.handleCommentDeleted(new Event.CommentDeleted(postId));

        // then
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("0");
    }
}