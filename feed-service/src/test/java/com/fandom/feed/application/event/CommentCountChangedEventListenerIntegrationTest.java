package com.fandom.feed.application.event;

import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(CommentCountChangedEventListener.class)
class CommentCountChangedEventListenerIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CommentCountChangedEventListener listener;

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

    @Nested
    @DisplayName("댓글 삭제 이벤트 발생")
    class HandleCommentDeleted {
        @Test
        @DisplayName("댓글 수 1 이상일 때 - Redis 댓글 수 감소")
        void handleCommentDeletedWhenMoreOne() {
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
        @DisplayName("댓글 수 0일 때 - 0 미만으로 내려가지 않음")
        void handleCommentDeletedWhenZero() {
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
}