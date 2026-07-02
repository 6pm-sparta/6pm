package com.fandom.feed.infra.redis;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimelineCacheService {
    private final StringRedisTemplate redisTemplate;

    /**
     * 타임라인 캐시에 게시글 ID를 추가하는 메서드
     */
    public void addPosts(List<UUID> userIds, UUID postId, long score) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (UUID userId : userIds) {
                String key = RedisKeyPrefix.TIMELINE + userId;
                stringConn.zAdd(key, score, postId.toString());
                stringConn.zRemRange(key, 0, -(FeedPolicy.MAX_CACHE_SIZE + 1));
            }
            return null;
        });
    }

    /**
     * 타임라인 캐시에서 게시글 ID를 삭제하는 메서드
     */
    public void removePosts(List<UUID> userIds, UUID postId) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (UUID userId : userIds) {
                String key = RedisKeyPrefix.TIMELINE + userId;
                stringConn.zRem(key, postId.toString());
            }
            return null;
        });
    }
}