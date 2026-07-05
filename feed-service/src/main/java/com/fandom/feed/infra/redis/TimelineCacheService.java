package com.fandom.feed.infra.redis;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimelineCacheService {
    private final StringRedisTemplate redisTemplate;

    @Value("${cache.ttl.timeline}")
    private long timelineTtl;

    /** 타임라인 캐시 존재 여부를 확인하는 메서드 */
    public boolean exists(UUID userId) {
        return Objects.requireNonNullElse(redisTemplate.hasKey(resolveKey(userId)), false);
    }

    /**
     * 타임라인 캐시에서 게시글 ID 목록을 조회하는 메서드<br>
     * - 5페이지 초과 시 null 반환
     */
    public List<UUID> getPostIds(UUID userId, UUID cursor) {
        String key = resolveKey(userId);

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
     * 타임라인 캐시에 게시글 ID를 추가하는 메서드<br>
     * - 이미 워밍업된 사용자에게만 적용
     */
    public void addPost(List<UUID> userIds, UUID postId, long score) {
        List<UUID> warmedUserIds = filterWarmedUserIds(userIds);
        if (warmedUserIds.isEmpty()) return;

        String member = postId.toString();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            warmedUserIds.forEach(userId -> {
                String key = resolveKey(userId);
                stringConn.zAdd(key, score, member);
                stringConn.zRemRange(key, 0, -(FeedPolicy.MAX_CACHE_SIZE + 1));
            });
            return null;
        });
    }

    /**
     * 타임라인 캐시에 게시글 ID를 추가하는 워밍업 메서드<br>
     * - 캐시 만료 시간 설정 포함
     */
    public void addPostsForWarm(UUID userId, List<UUID> postIds) {
        String key = resolveKey(userId);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            postIds.forEach(postId -> {
                long score = UuidV7TimestampExtractor.extract(postId);
                stringConn.zAdd(key, score, postId.toString());
            });
            stringConn.zAdd(key, -1, FeedPolicy.WARMED_MARKER);
            stringConn.expire(key, timelineTtl);
            return null;
        });
    }

    /** 타임라인 캐시에서 게시글 ID를 삭제하는 메서드 */
    public void removePost(List<UUID> userIds, UUID postId) {
        String member = postId.toString();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            userIds.forEach(userId -> stringConn.zRem(resolveKey(userId), member));
            return null;
        });
    }

    /** 타임라인 캐시가 이미 존재하는 사용자만 반환하는 메서드 */
    private List<UUID> filterWarmedUserIds(List<UUID> userIds) {
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            userIds.forEach(userId -> stringConn.exists(resolveKey(userId)));
            return null;
        });

        List<UUID> warmedUserIds = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++)
            if (Boolean.TRUE.equals(results.get(i)))
                warmedUserIds.add(userIds.get(i));
        return warmedUserIds;
    }

    /** 사용자 ID에 따라 Redis 키를 반환하는 메서드 */
    private String resolveKey(UUID userId) {
        return RedisKeyPrefix.TIMELINE + userId;
    }
}