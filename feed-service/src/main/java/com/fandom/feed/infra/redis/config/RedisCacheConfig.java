package com.fandom.feed.infra.redis.config;

import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisCacheConfig {
    private final ObjectMapper objectMapper;

    @Value("${cache.ttl.default}")
    private long defaultTtl;

    @Value("${cache.ttl.post-detail}")
    private long postDetailTtl;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();

        cacheObjectMapper.registerModule(new JavaTimeModule());
        cacheObjectMapper.registerModule(new ParameterNamesModule());
        cacheObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<PostDetailCache> postDetailSerializer = new Jackson2JsonRedisSerializer<>(cacheObjectMapper, PostDetailCache.class);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtl))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(postDetailSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(RedisKeyPrefix.POST_DETAIL, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(postDetailTtl))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(postDetailSerializer)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}