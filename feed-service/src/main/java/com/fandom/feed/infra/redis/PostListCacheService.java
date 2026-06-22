package com.fandom.feed.infra.redis;

import com.fandom.feed.application.policy.PostPolicy;
import com.fandom.feed.application.policy.ReactionSort;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostListCacheService {
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 정렬 기준에 따라 게시글 ID 목록을 조회하는 메서드<br>
     * - 5페이지 초과 시 null 반환
     */
    public List<UUID> getPostIds(ReactionSort sort, UUID cursor) {
        String key = resolveKey(sort);

        Double cursorScore = cursor != null ? redisTemplate.opsForZSet().score(key, cursor.toString()) : null;

        // cursor는 있는데 캐시에 없으면 5페이지 초과
        if (cursor != null && cursorScore == null) return null;

        Set<String> ids = switch (sort) {
            case LATEST -> redisTemplate.opsForZSet()
                    .reverseRangeByScore(
                            key,
                            0, cursorScore != null ? cursorScore - 1 : Double.MAX_VALUE,
                            0, PostPolicy.PAGE_SIZE + 1
                    );
            case OLDEST -> redisTemplate.opsForZSet()
                    .rangeByScore(
                            key,
                            cursorScore != null ? cursorScore + 1 : 0, Double.MAX_VALUE,
                            0, PostPolicy.PAGE_SIZE + 1
                    );
        };

        if (ids == null) return List.of();
        return ids.stream().map(UUID::fromString).toList();
    }

    /**
     * 캐시가 1페이지 이상인지 확인하는 메서드
     */
    public boolean isCacheReady(ReactionSort sort) {
        Long size = redisTemplate.opsForZSet().size(resolveKey(sort));
        return size != null && size >= PostPolicy.PAGE_SIZE;
    }

    /**
     * 캐시에 게시글 ID를 추가하는 메서드
     */
    public void addPost(UUID postId, ReactionSort sort) {
        String key = resolveKey(sort);
        String member = postId.toString();

        // UUID v7의 timestamp 추출해서 score로 사용
        long score = (postId.getMostSignificantBits() >>> 16);

        redisTemplate.opsForZSet().add(key, member, score);

        // MAX_CACHE_SIZE 초과 시 게시글 ID 제거
        if (sort == ReactionSort.LATEST)
            redisTemplate.opsForZSet().removeRange(key, 0, -(PostPolicy.MAX_CACHE_SIZE + 1));
        else
            redisTemplate.opsForZSet().removeRange(key, PostPolicy.MAX_CACHE_SIZE, -1);
    }

    /**
     * 캐시에 게시글 ID를 삭제하는 메서드
     */
    public void removePost(UUID postId) {
        String member = postId.toString();
        allKeys().forEach(key -> redisTemplate.opsForZSet().remove(key, member));
    }

    /**
     * 정렬 기준에 따라 Redis 키를 반환하는 메서드
     */
    private String resolveKey(ReactionSort sort) {
        return switch (sort) {
            case LATEST -> RedisKeyPrefix.POST_LIST_LATEST;
            case OLDEST -> RedisKeyPrefix.POST_LIST_OLDEST;
        };
    }

    /**
     * 목록 캐시에서 사용하는 모든 Redis 키를 반환하는 메서드
     */
    private List<String> allKeys() {
        return List.of(RedisKeyPrefix.POST_LIST_LATEST, RedisKeyPrefix.POST_LIST_OLDEST);
    }

    /**
     * 정렬순에 따라 TTL를 설정하는 메서드
     */
    public void expireCache(ReactionSort sort) {
        Duration ttl = switch (sort) {
            case LATEST -> Duration.ofMinutes(3);
            case OLDEST -> Duration.ofMinutes(10);
        };
        redisTemplate.expire(resolveKey(sort), ttl);
    }
}