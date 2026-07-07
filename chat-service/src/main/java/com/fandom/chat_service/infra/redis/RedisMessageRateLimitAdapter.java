package com.fandom.chat_service.infra.redis;

import com.fandom.chat_service.application.port.MessageRateLimitPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisMessageRateLimitAdapter implements MessageRateLimitPort {

    private final StringRedisTemplate redisTemplate;

    private final int slowModeSeconds;
    private final int dedupeWindowSeconds;

    public RedisMessageRateLimitAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${chat.message-control.slow-mode-seconds:5}") int slowModeSeconds,
            @Value("${chat.message-control.dedupe-window-seconds:30}") int dedupeWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.slowModeSeconds = slowModeSeconds;
        this.dedupeWindowSeconds = dedupeWindowSeconds;
    }

    @Override
    public boolean tryAcquireSlowMode(UUID roomId, UUID userId) {
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(slowKey(roomId, userId), "1", Duration.ofSeconds(slowModeSeconds));
        return Boolean.TRUE.equals(set);
    }

    @Override
    public boolean isDuplicate(UUID roomId, UUID userId, String content) {
        String key = dedupeKey(roomId, userId);
        String hash = Integer.toString(content.hashCode());
        String prev = redisTemplate.opsForValue().get(key);
        redisTemplate.opsForValue().set(key, hash, Duration.ofSeconds(dedupeWindowSeconds));
        return hash.equals(prev);
    }

    private String slowKey(UUID roomId, UUID userId) {
        return "chat:slow:" + roomId + ":" + userId;
    }

    private String dedupeKey(UUID roomId, UUID userId) {
        return "chat:last:" + roomId + ":" + userId;
    }
}
