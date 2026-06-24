package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
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
     * 작성자 ID에 따라 게시글 ID 목록을 조회하는 메서드<br>
     * - 5페이지 초과 시 null 반환
     */
    public List<UUID> getPostIds(UUID authorId, UUID cursor) {
        String key = resolveKey(authorId);

        Double cursorScore = (cursor != null) ? redisTemplate.opsForZSet().score(key, cursor.toString()) : null;

        // cursor는 있는데 캐시에 없으면 5페이지 초과
        if (cursor != null && cursorScore == null) return null;

        Set<String> ids = redisTemplate.opsForZSet().reverseRangeByScore(
                key,
                0, (cursorScore != null) ? cursorScore - 1 : Double.MAX_VALUE,
                0, FeedPolicy.PAGE_SIZE + 1
        );

        if (ids == null) return List.of();
        return ids.stream().map(UUID::fromString).toList();
    }

    /**
     * 캐시가 1페이지 이상인지 확인하는 메서드
     */
    public boolean isCacheReady(UUID authorId) {
        Long size = redisTemplate.opsForZSet().size(resolveKey(authorId));
        return size != null && size >= FeedPolicy.PAGE_SIZE;
    }

    /**
     * 캐시에 게시글 ID를 추가하는 메서드
     * - 워밍업 작업 아닐 시, 항상 작성자 ID 필수
     */
    public void addPost(UUID postId, UUID authorId, boolean isWarm) {
        String member = postId.toString();

        // UUID v7의 timestamp 추출해서 score로 사용
        long score = (postId.getMostSignificantBits() >>> 16);

        // 워밍업 작업
        if (isWarm) {
            redisTemplate.opsForZSet().add(resolveKey(authorId), member, score);
            return;
        }

        allKeys(authorId).forEach(key -> redisTemplate.opsForZSet().add(key, member, score));

        // MAX_CACHE_SIZE 초과 시 게시글 ID 제거
        allKeys(authorId).forEach(
                key -> redisTemplate.opsForZSet().removeRange(key, 0, -(FeedPolicy.MAX_CACHE_SIZE + 1))
        );
    }

    /**
     * 캐시에 게시글 ID를 삭제하는 메서드
     */
    public void removePost(UUID postId, UUID authorId) {
        String member = postId.toString();
        allKeys(authorId).forEach(key -> redisTemplate.opsForZSet().remove(key, member));
    }

    /**
     * 작성자 ID에 따라 Redis 키를 반환하는 메서드
     * - 작성자 ID가 null이면, feed:posts:all
     */
    private String resolveKey(UUID authorId) {
        if (authorId == null) return RedisKeyPrefix.POST_LIST_ALL;
        return RedisKeyPrefix.POST_LIST + authorId;
    }

    /**
     * 목록 캐시에서 사용하는 모든 Redis 키를 반환하는 메서드
     */
    private List<String> allKeys(UUID authorId) {
        return List.of(RedisKeyPrefix.POST_LIST_ALL, RedisKeyPrefix.POST_LIST + authorId);
    }

    /**
     * TTL를 설정하는 메서드
     */
    public void expireCache(UUID authorId) {
        Duration ttl = Duration.ofMinutes(3);
        redisTemplate.expire(resolveKey(authorId), ttl);
    }
}