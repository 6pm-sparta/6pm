package com.fandom.feed.infra.redis;

import com.fandom.feed.application.policy.PostSort;
import com.fandom.feed.infra.redis.config.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.fandom.feed.application.policy.PostPolicy.MAX_CACHE_SIZE;
import static com.fandom.feed.application.policy.PostPolicy.PAGE_SIZE;

@Service
@RequiredArgsConstructor
public class PostListCacheService {
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 정렬 기준에 따라 게시글 ID 목록을 조회하는 메서드 (null 반환 시 DB 조회 필요)
     */
    public List<UUID> getPostIds(PostSort sort, UUID cursor) {
        String key = resolveKey(sort);

        Double cursorScore = cursor != null ? redisTemplate.opsForZSet().score(key, cursor.toString()) : null;

        // cursor는 있는데 캐시에 없으면 5페이지 초과
        if (cursor != null && cursorScore == null) return null;

        Set<String> ids = switch (sort) {
            case LATEST -> redisTemplate.opsForZSet()
                    .reverseRangeByScore(key, 0, cursorScore != null ? cursorScore - 1 : Double.MAX_VALUE, 0, PAGE_SIZE + 1);
            case OLDEST -> redisTemplate.opsForZSet()
                    .rangeByScore(key, cursorScore != null ? cursorScore + 1 : 0, Double.MAX_VALUE, 0, PAGE_SIZE + 1);
        };

        if (ids == null) return List.of();
        return ids.stream().map(UUID::fromString).toList();
    }

    /**
     * 캐시가 1페이지 이상인지 확인하는 메서드
     */
    public boolean isCacheReady(PostSort sort) {
        Long size = redisTemplate.opsForZSet().size(resolveKey(sort));
        return size != null && size >= PAGE_SIZE;
    }

    /**
     * 캐시에 게시글 ID를 추가하는 메서드
     */
    public void addPost(UUID postId, LocalDateTime createdAt) {
        double score = createdAt.toInstant(ZoneOffset.UTC).toEpochMilli();
        String member = postId.toString();

        // 캐시에 추가하되, MAX_CACHE_SIZE 초과 시 가장 오래된 게시글 ID 제거
        allKeys().forEach(key -> {
            redisTemplate.opsForZSet().add(key, member, score);
            redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_CACHE_SIZE + 1));
        });
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
    private String resolveKey(PostSort sort) {
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
}