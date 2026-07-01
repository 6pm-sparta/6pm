package com.fandom.feed.infra.redis;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
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

    @Value("${cache.ttl.post-list}")
    private long postListTtl;

    /**
     * 작성자 ID에 따라 게시글 목록 캐시에서 ID 목록을 조회하는 메서드<br>
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
     * 게시글 목록 캐시가 1페이지 이상인지 확인하는 메서드
     */
    public boolean isCacheReady(UUID authorId) {
        Long size = redisTemplate.opsForZSet().size(resolveKey(authorId));
        return size != null && size >= FeedPolicy.PAGE_SIZE;
    }

    /**
     * 게시글 목록 캐시에 게시글 ID를 추가하는 메서드
     */
    public void addPost(UUID postId, UUID authorId) {
        String member = postId.toString();
        long score = UuidV7TimestampExtractor.extract(postId);
        List<String> keys = allKeys(authorId);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            keys.forEach(key -> {
                connection.zSetCommands().zAdd(key.getBytes(), score, member.getBytes());
                connection.zSetCommands().zRemRange(key.getBytes(), 0, -(FeedPolicy.MAX_CACHE_SIZE + 1));
            });
            return null;
        });
    }

    /**
     * 게시글 목록 캐시에 게시글 ID 목록을 추가하는 워밍업 메서드
     */
    public void addPostsForWarm(List<UUID> postIds, UUID authorId) {
        if (postIds.isEmpty()) return;
        String key = resolveKey(authorId);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            postIds.forEach(postId -> {
                long score = UuidV7TimestampExtractor.extract(postId);
                connection.zSetCommands().zAdd(key.getBytes(), score, postId.toString().getBytes());
            });
            return null;
        });
    }

    /**
     * 게시글 목록 캐시에서 게시글 ID를 삭제하는 메서드
     */
    public void removePost(UUID postId, UUID authorId) {
        String member = postId.toString();
        List<String> keys = allKeys(authorId);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            keys.forEach(key -> connection.zSetCommands().zRem(key.getBytes(), member.getBytes()));
            return null;
        });
    }

    /**
     * 게시글 목록 캐시에서 작성자 ID의 모든 게시글 ID를 삭제하는 메서드
     */
    public void removeAllByAuthorId(List<UUID> postIds, UUID authorId) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // feed:posts:all는 하나씩 제거
            postIds.forEach(postId ->
                    connection.zSetCommands().zRem(RedisKeyPrefix.POST_LIST_ALL.getBytes(), postId.toString().getBytes())
            );
            // feed:posts:{authorId}는 한번에 삭제
            connection.keyCommands().del((RedisKeyPrefix.POST_LIST + authorId).getBytes());
            return null;
        });
    }

    /**
     * 작성자 ID에 따라 Redis 키를 반환하는 메서드<br>
     * - 작성자 ID가 null이면, feed:posts:all
     */
    private String resolveKey(UUID authorId) {
        if (authorId == null) return RedisKeyPrefix.POST_LIST_ALL;
        return RedisKeyPrefix.POST_LIST + authorId;
    }

    /**
     * 게시글 목록 캐시에서 사용할 Redis 키를 모두 반환하는 메서드
     */
    private List<String> allKeys(UUID authorId) {
        return List.of(RedisKeyPrefix.POST_LIST_ALL, RedisKeyPrefix.POST_LIST + authorId);
    }

    /**
     * TTL를 설정하는 메서드
     */
    public void expireCache(UUID authorId) {
        redisTemplate.expire(resolveKey(authorId), Duration.ofSeconds(postListTtl));
    }
}