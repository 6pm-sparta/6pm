package com.fandom.feed.infra.redis;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostListCacheService {
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cache.ttl.post-list}")
    private long postListTtl;

    /**
     * 작성자 ID에 따라 게시글 목록 캐시에서 게시글 ID 목록을 조회하는 메서드<br>
     * - 5페이지 초과 시 null 반환
     */
    public List<UUID> getPostIds(UUID authorId, UUID cursor) {
        String key = resolveKey(authorId);

        Double cursorScore = (cursor != null) ? redisTemplate.opsForZSet().score(key, cursor.toString()) : null;

        // cursor는 있는데 캐시에 없으면 5페이지 초과
        if (cursor != null && cursorScore == null) return null;

        Set<String> postIds = redisTemplate.opsForZSet().reverseRangeByScore(
                key,
                0, (cursorScore != null) ? cursorScore - 1 : Double.MAX_VALUE,
                0, FeedPolicy.PAGE_SIZE + 1
        );

        if (postIds == null) return List.of();
        return postIds.stream().map(UUID::fromString).toList();
    }

    /**
     * 팔로잉 ID 목록으로 게시글 목록 캐시에서 게시글 ID 목록을 조회하는 메서드<br>
     * - 각 팔로잉별로 5페이지 초과 시 null로 표시
     */
    public Map<UUID, List<UUID>> getPostIdsBatch(List<UUID> followingIds, UUID cursor) {
        List<Object> cursorScores = (cursor != null)
                ? redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    StringRedisConnection stringConn = (StringRedisConnection) connection;
                    followingIds.forEach(authorId -> stringConn.zScore(resolveKey(authorId), cursor.toString()));
                    return null;
                })
                : null;

        List<Object> rangeResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (int i = 0; i < followingIds.size(); i++) {
                String key = resolveKey(followingIds.get(i));
                Double cursorScore = (cursorScores != null) ? (Double) cursorScores.get(i) : null;
                double max = (cursorScore != null) ? cursorScore - 1 : Double.MAX_VALUE;

                stringConn.zRevRangeByScore(key, 0, max, 0, FeedPolicy.PAGE_SIZE + 1);
            }
            return null;
        });

        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        for (int i = 0; i < followingIds.size(); i++) {
            UUID authorId = followingIds.get(i);
            Double cursorScore = (cursorScores != null) ? (Double) cursorScores.get(i) : null;

            // cursor는 있는데 캐시에 없으면 5페이지 초과
            if (cursor != null && cursorScore == null) {
                result.put(authorId, null);
                continue;
            }

            @SuppressWarnings("unchecked")
            Set<String> raw = (Set<String>) rangeResults.get(i);
            List<UUID> postIds = (raw == null) ? List.of() : raw.stream().map(UUID::fromString).toList();
            result.put(authorId, postIds);
        }
        return result;
    }

    /** 게시글 목록 캐시가 워밍업 되었는지 확인하는 메서드 */
    public boolean isCacheReady(UUID authorId) {
        Double score = redisTemplate.opsForZSet().score(resolveKey(authorId), FeedPolicy.WARMED_MARKER);
        return score != null;
    }

    /** 게시글 목록 캐시에 게시글 ID를 추가하는 메서드 */
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
     * 게시글 목록 캐시에 게시글 ID 목록을 추가하는 워밍업 메서드<br>
     *  - 만료 시간 설정 포함
     */
    public void addPostsForWarm(List<UUID> postIds, UUID authorId) {
        String key = resolveKey(authorId);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            postIds.forEach(postId -> {
                long score = UuidV7TimestampExtractor.extract(postId);
                connection.zSetCommands().zAdd(key.getBytes(), score, postId.toString().getBytes());
            });
            connection.zSetCommands().zAdd(key.getBytes(), -1, FeedPolicy.WARMED_MARKER.getBytes());
            connection.keyCommands().expire(key.getBytes(), postListTtl);
            return null;
        });
    }

    /** 게시글 목록 캐시에서 게시글 ID를 삭제하는 메서드 */
    public void removePost(UUID postId, UUID authorId) {
        String member = postId.toString();
        List<String> keys = allKeys(authorId);

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            keys.forEach(key -> connection.zSetCommands().zRem(key.getBytes(), member.getBytes()));
            return null;
        });
    }

    /** 게시글 목록 캐시에서 작성자 ID의 모든 게시글 ID를 삭제하는 메서드 */
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

    /** 게시글 목록 캐시에서 사용할 Redis 키를 모두 반환하는 메서드 */
    private List<String> allKeys(UUID authorId) {
        return List.of(RedisKeyPrefix.POST_LIST_ALL, RedisKeyPrefix.POST_LIST + authorId);
    }
}