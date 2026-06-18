package com.fandom.feed.infra.redis;

import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostReactionService {
    private final RedisTemplate<String, String> redisTemplate;

    private static final String COMMENT_KEY = "comments:";
    private static final String LIKE_KEY = "likes:";

    private static final Duration REACTION_TTL = Duration.ofMinutes(10);

    public PostCache.ReactionInfo getReactionInfo(UUID id, UUID userId, Long commentCount) {
        return new PostCache.ReactionInfo(getCommentCount(id, commentCount), getLikeCount(id), isLiked(id, userId));
    }

    private long getCommentCount(UUID id, long dbCount) {
        String key = COMMENT_KEY + id;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) return Long.parseLong(cached);

        // 캐시 미스 발생
        redisTemplate.opsForValue().set(key, String.valueOf(dbCount), REACTION_TTL);
        return dbCount;
    }

    // TODO: Like 도메인 연동 필요 [P1]
    private long getLikeCount(UUID id) {
        return Optional.ofNullable(redisTemplate.opsForSet().size(LIKE_KEY + id)).orElse(0L);
    }

    private boolean isLiked(UUID id, UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(LIKE_KEY + id, userId.toString()));
    }
}