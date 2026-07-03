package com.fandom.feed.application.event;

import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.constant.RedisScript;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CommentCountChangedEventListener {
    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCreated(Event.CommentCreated event) {
        redisTemplate.opsForValue().increment(RedisKeyPrefix.COMMENT_COUNT + event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentDeleted(Event.CommentDeleted event) {
        redisTemplate.execute(RedisScript.DECREMENT_MIN_ZERO, List.of(RedisKeyPrefix.COMMENT_COUNT + event.postId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentAllDeletedBatch(Event.CommentAllDeleted event) {
        redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            event.postIds().forEach(postId ->
                    connection.keyCommands().del((RedisKeyPrefix.COMMENT_COUNT + postId).getBytes())
            );
            return null;
        });
    }
}