package com.fandom.auth_service.auth.infrastructure.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@Import({RedisAutoConfiguration.class, RedisTokenRepository.class})
@DisplayName("RedisTokenRepository 통합 테스트")
class RedisTokenRepositoryIntegrationTest {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String ACCESS_BLACKLIST_KEY_PREFIX = "blacklist:access:";
    private static final String USER_BLACKLIST_KEY_PREFIX = "blacklist:user:";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private RedisTokenRepository tokenRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Refresh Token을 TTL과 함께 저장하고 삭제할 수 있다")
    void saveAndDeleteRefreshToken() {
        UUID userId = UUID.randomUUID();
        String tokenId = "refresh-token-id";
        Duration ttl = Duration.ofMinutes(30);

        tokenRepository.saveRefreshToken(userId, tokenId, ttl);

        String key = refreshKey(userId, tokenId);
        assertThat(tokenRepository.existsRefreshToken(userId, tokenId)).isTrue();
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, ttl.toSeconds());

        tokenRepository.deleteRefreshToken(userId, tokenId);

        assertThat(tokenRepository.existsRefreshToken(userId, tokenId)).isFalse();
    }

    @Test
    @DisplayName("Access Token blacklist를 양수 TTL과 함께 저장한다")
    void blacklistAccessTokenWithPositiveTtl() {
        String tokenId = "access-token-id";
        Duration ttl = Duration.ofMinutes(10);

        tokenRepository.blacklistAccessToken(tokenId, ttl);

        String key = accessBlacklistKey(tokenId);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(key))).isTrue();
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, ttl.toSeconds());
    }

    @Test
    @DisplayName("Access Token blacklist TTL이 0 이하이면 저장하지 않는다")
    void blacklistAccessTokenSkipsNonPositiveTtl() {
        tokenRepository.blacklistAccessToken("zero-ttl", Duration.ZERO);
        tokenRepository.blacklistAccessToken("negative-ttl", Duration.ofSeconds(-1));

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(accessBlacklistKey("zero-ttl")))).isFalse();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(accessBlacklistKey("negative-ttl")))).isFalse();
    }

    @Test
    @DisplayName("사용자 토큰 무효화 시 모든 Refresh Token을 삭제하고 사용자 blacklist를 TTL과 함께 저장한다")
    void revokeUserTokensDeletesAllRefreshTokensAndBlacklistsUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Duration refreshTtl = Duration.ofHours(1);
        Duration accessTtl = Duration.ofMinutes(30);

        tokenRepository.saveRefreshToken(userId, "token-1", refreshTtl);
        tokenRepository.saveRefreshToken(userId, "token-2", refreshTtl);
        tokenRepository.saveRefreshToken(otherUserId, "token-3", refreshTtl);

        tokenRepository.revokeUserTokens(userId, accessTtl);

        assertThat(tokenRepository.existsRefreshToken(userId, "token-1")).isFalse();
        assertThat(tokenRepository.existsRefreshToken(userId, "token-2")).isFalse();
        assertThat(tokenRepository.existsRefreshToken(otherUserId, "token-3")).isTrue();

        String userBlacklistKey = userBlacklistKey(userId);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(userBlacklistKey))).isTrue();
        assertThat(redisTemplate.getExpire(userBlacklistKey)).isBetween(1L, accessTtl.toSeconds());
    }

    private String refreshKey(UUID userId, String tokenId) {
        return REFRESH_KEY_PREFIX + userId + ":" + tokenId;
    }

    private String accessBlacklistKey(String tokenId) {
        return ACCESS_BLACKLIST_KEY_PREFIX + tokenId;
    }

    private String userBlacklistKey(UUID userId) {
        return USER_BLACKLIST_KEY_PREFIX + userId;
    }
}
