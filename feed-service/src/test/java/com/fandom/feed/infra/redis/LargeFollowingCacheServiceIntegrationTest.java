package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.config.RedisIntegrationTestSupport;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "cache.ttl.large-following=600")
@Import(LargeFollowingCacheService.class)
class LargeFollowingCacheServiceIntegrationTest extends RedisIntegrationTestSupport {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LargeFollowingCacheService largeFollowingCacheService;

    @Nested
    @DisplayName("대형 크리에이터 팔로잉 목록 캐시에서 조회")
    class GetLargeFollowingIds {
        @Test
        @DisplayName("저장된 팔로잉 ID 목록 반환")
        void getLargeFollowingIds() {
            // given
            UUID userId = UUID.randomUUID();
            UUID authorId1 = UUID.randomUUID();
            UUID authorId2 = UUID.randomUUID();
            largeFollowingCacheService.addLargeFollowing(userId, List.of(authorId1, authorId2));

            // when
            List<UUID> result = largeFollowingCacheService.getLargeFollowingIds(userId);

            // then
            assertThat(result).containsExactlyInAnyOrder(authorId1, authorId2);
        }

        @Test
        @DisplayName("빈 목록으로 저장됨 - EMPTY_MARKER를 제외하고 빈 목록 반환")
        void getLargeFollowingIdsWhenEmpty() {
            // given
            UUID userId = UUID.randomUUID();
            largeFollowingCacheService.addLargeFollowing(userId, List.of());

            // when
            List<UUID> result = largeFollowingCacheService.getLargeFollowingIds(userId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("캐시 없음 - 빈 목록 반환")
        void getLargeFollowingIdsWhenNoCache() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            List<UUID> result = largeFollowingCacheService.getLargeFollowingIds(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("대형 크리에이터 팔로잉 목록 캐시에 추가")
    class AddLargeFollowing {
        @Test
        @DisplayName("팔로잉 ID 목록이 캐시에 저장")
        void addLargeFollowing() {
            // given
            UUID userId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();

            // when
            largeFollowingCacheService.addLargeFollowing(userId, List.of(authorId));

            // then
            Set<String> members = redisTemplate.opsForSet().members(RedisKeyPrefix.LARGE_FOLLOWING + userId);
            assertThat(members).containsExactly(authorId.toString());
        }

        @Test
        @DisplayName("빈 목록 - EMPTY_MARKER가 저장")
        void addLargeFollowingWithEmptyList() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            largeFollowingCacheService.addLargeFollowing(userId, List.of());

            // then
            Set<String> members = redisTemplate.opsForSet().members(RedisKeyPrefix.LARGE_FOLLOWING + userId);
            assertThat(members).containsExactly(FeedPolicy.EMPTY_MARKER);
        }

        @Test
        @DisplayName("TTL 설정")
        void addLargeFollowingSetsExpire() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            largeFollowingCacheService.addLargeFollowing(userId, List.of(UUID.randomUUID()));

            // then
            Long ttl = redisTemplate.getExpire(RedisKeyPrefix.LARGE_FOLLOWING + userId);
            assertThat(ttl).isPositive();
        }
    }
}