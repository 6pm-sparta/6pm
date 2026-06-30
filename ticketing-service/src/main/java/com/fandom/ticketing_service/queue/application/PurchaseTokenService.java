package com.fandom.ticketing_service.queue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseTokenService {

    private static final String PURCHASE_TOKEN_KEY = "purchase-token:%s:%s";
    static final Duration TOKEN_TTL = Duration.ofSeconds(600);

    private final RedisTemplate<String, String> redisTemplate;

    public boolean issue(UUID showId, UUID userId) {
        String key = PURCHASE_TOKEN_KEY.formatted(showId, userId);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", TOKEN_TTL));
    }

    public boolean exists(UUID showId, UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PURCHASE_TOKEN_KEY.formatted(showId, userId)));
    }
}
