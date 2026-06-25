package com.fandom.chat_service.infra.redis;

import com.fandom.chat_service.application.port.OnlineStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisOnlineStateAdapter implements OnlineStatePort {

    private static final String ONLINE_KEY = "chat:online";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void markOnline(UUID userId) {
        redisTemplate.opsForSet().add(ONLINE_KEY, userId.toString());
    }

    @Override
    public void markOffline(UUID userId) {
        redisTemplate.opsForSet().remove(ONLINE_KEY, userId.toString());
    }

    @Override
    public boolean isOnline(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_KEY, userId.toString()));
    }

    @Override
    public List<UUID> filterOnline(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Object[] members = userIds.stream().map(UUID::toString).toArray();
        Map<Object, Boolean> result = redisTemplate.opsForSet().isMember(ONLINE_KEY, members); // SMISMEMBER 1회

        List<UUID> online = new ArrayList<>();
        for (UUID userId : userIds) {
            if (result != null && Boolean.TRUE.equals(result.get(userId.toString()))) {
                online.add(userId);
            }
        }
        return online;
    }
}
