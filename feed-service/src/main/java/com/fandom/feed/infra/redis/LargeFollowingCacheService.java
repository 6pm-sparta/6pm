package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LargeFollowingCacheService {
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cache.ttl.large-following}")
    private long largeFollowingTtl;

    /** 대형 크리에이터 팔로잉 목록 캐시 존재 여부를 확인하는 메서드 */
    public boolean exists(UUID userId) {
        return Objects.requireNonNullElse(redisTemplate.hasKey(resolveKey(userId)), false);
    }

    /** 대형 크리에이터 팔로잉 목록 캐시에서 팔로잉 ID 목록을 조회하는 메서드 */
    public List<UUID> getLargeFollowingIds(UUID userId) {
        Set<String> followingIds = redisTemplate.opsForSet().members(resolveKey(userId));
        if (followingIds == null) return List.of();

        return followingIds.stream()
                .filter(followingId -> !FeedPolicy.EMPTY_MARKER.equals(followingId))
                .map(UUID::fromString)
                .toList();
    }

    /** 대형 크리에이터 팔로잉 목록 캐시에 팔로잉 ID 목록을 추가하는 메서드 */
    public void addLargeFollowing(UUID userId, List<UUID> followingIds) {
        String key = resolveKey(userId);

        if (followingIds.isEmpty()) {
            redisTemplate.opsForSet().add(key, FeedPolicy.EMPTY_MARKER);
        } else {
            String[] members = followingIds.stream().map(UUID::toString).toArray(String[]::new);
            redisTemplate.opsForSet().add(key, members);
        }
        redisTemplate.expire(key, Duration.ofSeconds(largeFollowingTtl));
    }

    /** 사용자 ID에 따라 Redis 키를 반환하는 메서드 */
    private String resolveKey(UUID userId) {
        return RedisKeyPrefix.LARGE_FOLLOWING + userId;
    }
}