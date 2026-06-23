package com.fandom.auth_service.auth.infrastructure.redis;

import com.fandom.auth_service.auth.domain.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisTokenRepository implements TokenRepository {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String ACCESS_BLACKLIST_KEY_PREFIX = "blacklist:access:";
    private static final String USER_BLACKLIST_KEY_PREFIX = "blacklist:user:";
    private static final String STORED_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveRefreshToken(UUID userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(refreshKey(userId, tokenId), STORED_VALUE, ttl);
    }

    @Override
    public boolean existsRefreshToken(UUID userId, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(refreshKey(userId, tokenId)));
    }

    @Override
    public void deleteRefreshToken(UUID userId, String tokenId) {
        redisTemplate.delete(refreshKey(userId, tokenId));
    }

    @Override
    public void blacklistAccessToken(String tokenId, Duration ttl) {
        if (!ttl.isPositive()) {
            return;
        }
        redisTemplate.opsForValue().set(accessBlacklistKey(tokenId), STORED_VALUE, ttl);
    }

    @Override
    public void revokeUserTokens(UUID userId, Duration accessTokenTtl) {
        deleteRefreshTokensByUserId(userId);
        if (accessTokenTtl.isPositive()) {
            redisTemplate.opsForValue().set(userBlacklistKey(userId), STORED_VALUE, accessTokenTtl);
        }
    }

    private void deleteRefreshTokensByUserId(UUID userId) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(refreshKeyPattern(userId))
                .count(100)
                .build();
        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String refreshKey(UUID userId, String tokenId) {
        return REFRESH_KEY_PREFIX + userId + ":" + tokenId;
    }

    private String refreshKeyPattern(UUID userId) {
        return REFRESH_KEY_PREFIX + userId + ":*";
    }

    private String accessBlacklistKey(String tokenId) {
        return ACCESS_BLACKLIST_KEY_PREFIX + tokenId;
    }

    private String userBlacklistKey(UUID userId) {
        return USER_BLACKLIST_KEY_PREFIX + userId;
    }
}
