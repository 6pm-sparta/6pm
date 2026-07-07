package com.fandom.feed.infra.redis.config;

import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DataRedisTest
@Import({RedisCacheConfig.class, JacksonAutoConfiguration.class})
class RedisCacheConfigTest {
    @Autowired
    private RedisCacheManager cacheManager;

    @Test
    @DisplayName("캐시 직렬화 설정 확인")
    void cacheManagerSerializationConfig() {
        // given
        RedisCacheConfiguration config = cacheManager.getCacheConfigurations().get(RedisKeyPrefix.POST_DETAIL);

        assertThat(config).isNotNull();
        Cache cache = cacheManager.getCache(RedisKeyPrefix.POST_DETAIL);
        PostDetailCache cachedPost = new PostDetailCache(
                UUID.randomUUID(), null, "내용", List.of(), LocalDateTime.now(), LocalDateTime.now()
        );

        // when & then
        assertDoesNotThrow(() -> {
            Assertions.assertNotNull(cache);
            cache.put("test-key", cachedPost);
        });
    }
}