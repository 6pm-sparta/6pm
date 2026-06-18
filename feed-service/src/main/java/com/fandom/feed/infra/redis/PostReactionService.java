package com.fandom.feed.infra.redis;

import com.fandom.feed.infra.redis.config.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostReactionService {
    private final RedisTemplate<String, String> redisTemplate;

    public PostCache.ReactionInfo getReactionInfo(UUID id, UUID userId) {
        return new PostCache.ReactionInfo(getCommentCount(id), getLikeCount(id), isLiked(id, userId));
    }

    // TODO: Comment 도메인 연동 필요 [P1]
    private long getCommentCount(UUID id) {
        String count = redisTemplate.opsForValue().get(RedisKeyPrefix.COMMENT_COUNT + id);
        return (count != null) ? Long.parseLong(count) : 0L;
    }

    // TODO: Like 도메인 연동 필요 [P1]
    private long getLikeCount(UUID id) {
        Long size = redisTemplate.opsForSet().size(RedisKeyPrefix.LIKE_SET + id);
        return (size != null) ? size : 0L;
    }

    private boolean isLiked(UUID id, UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + id, userId.toString()));
    }
}