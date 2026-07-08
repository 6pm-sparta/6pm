package com.fandom.feed.infra.scheduler;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class LikeSyncSchedulerTest {
    @Autowired
    private LikeSyncScheduler likeSyncScheduler;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.write.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.write.username", postgres::getUsername);
        registry.add("spring.datasource.write.password", postgres::getPassword);
        registry.add("spring.datasource.write.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.datasource.read.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.read.username", postgres::getUsername);
        registry.add("spring.datasource.read.password", postgres::getPassword);
        registry.add("spring.datasource.read.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("Redis에만 있는 좋아요가 DB에 동기화")
    void syncLikes() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE + postId, userId.toString());

        // When
        likeSyncScheduler.syncLikes();

        // Then
        List<Like> likes = likeRepository.findAllByPostId(postId);
        assertThat(likes).extracting(Like::getUserId).contains(userId);

        // Cleanup
        redisTemplate.delete(RedisKeyPrefix.LIKE + postId);
    }
}