package com.fandom.chat_service.infra.redis;

import com.fandom.chat_service.application.port.RoomMemberCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisRoomMemberCacheAdapter implements RoomMemberCachePort {

    private static final String KEY_PREFIX = "chat:room:";
    private static final String KEY_SUFFIX = ":members";
    private static final long TTL_BASE_SECONDS = 3600;
    private static final long TTL_JITTER_SECONDS = 600; // 만료 편차 위한 지터

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean exists(UUID roomId) {
        return redisTemplate.hasKey(key(roomId));
    }

    @Override
    public Set<UUID> getMembers(UUID roomId) {
        Set<String> members = redisTemplate.opsForSet().members(key(roomId));
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    @Override
    public void cacheMembers(UUID roomId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String key = key(roomId);
        String[] values = userIds.stream().map(UUID::toString).toArray(String[]::new);
        redisTemplate.opsForSet().add(key, values);
        redisTemplate.expire(key, ttlWithJitter());
    }

    @Override
    public void addIfPresent(UUID roomId, UUID userId) {
        String key = key(roomId);
        if (redisTemplate.hasKey(key)) {
            redisTemplate.opsForSet().add(key, userId.toString());
            redisTemplate.expire(key, ttlWithJitter());
        }
    }

    @Override
    public void remove(UUID roomId, UUID userId) {
        redisTemplate.opsForSet().remove(key(roomId), userId.toString());
    }

    @Override
    public void evict(UUID roomId) {
        redisTemplate.delete(key(roomId));
    }

    private String key(UUID roomId) {
        return KEY_PREFIX + roomId + KEY_SUFFIX;
    }

    private Duration ttlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1);
        return Duration.ofSeconds(TTL_BASE_SECONDS + jitter);
    }
}
