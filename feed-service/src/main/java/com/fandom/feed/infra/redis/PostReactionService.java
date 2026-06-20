package com.fandom.feed.infra.redis;

import com.fandom.feed.infra.redis.dto.PostCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.fandom.feed.infra.redis.config.RedisKeyPrefix.COMMENT_COUNT;
import static com.fandom.feed.infra.redis.config.RedisKeyPrefix.LIKE_SET;

@Service
@RequiredArgsConstructor
public class PostReactionService {
    private final RedisTemplate<String, String> redisTemplate;

    public PostCache.ReactionInfo getReactionInfo(UUID id, UUID userId) {
        return new PostCache.ReactionInfo(getCommentCount(id), getLikeCount(id), isLiked(id, userId));
    }

    // TODO: Comment, Like 도메인 연동 필요 [P1]
    public List<PostCache.ReactionInfo> getReactionInfoBatch(List<UUID> ids, UUID userId) {
        // Redis Pipeline을 통해 여러 명령을 한 번의 네트워크 요청으로 처리
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            ids.forEach(id -> {
                String likeKey = LIKE_SET + id;
                String commentKey = COMMENT_COUNT + id;

                // 커맨드 순서: commentCount → likeCount → isLiked
                connection.stringCommands().get(commentKey.getBytes());
                connection.setCommands().sCard(likeKey.getBytes());

                if (userId != null)
                    connection.setCommands().sIsMember(likeKey.getBytes(), userId.toString().getBytes());
            });
            return null;
        });

        // 사용자 식별자 있으면 3개, 없으면 2개 묶음
        int step = userId != null ? 3 : 2;
        List<PostCache.ReactionInfo> reactionInfos = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            int base = i * step;

            long commentCount = results.get(base) != null ? Long.parseLong(results.get(base).toString()) : 0L;
            long likeCount = results.get(base + 1) != null ? (Long) results.get(base + 1) : 0L;
            boolean liked = userId != null && Boolean.TRUE.equals(results.get(base + 2));

            reactionInfos.add(new PostCache.ReactionInfo(commentCount, likeCount, liked));
        }

        return reactionInfos;
    }

    // TODO: Comment 도메인 연동 필요 [P1]
    private long getCommentCount(UUID id) {
        String count = redisTemplate.opsForValue().get(COMMENT_COUNT + id);
        return (count != null) ? Long.parseLong(count) : 0L;
    }

    // TODO: Like 도메인 연동 필요 [P1]
    private long getLikeCount(UUID id) {
        Long size = redisTemplate.opsForSet().size(LIKE_SET + id);
        return (size != null) ? size : 0L;
    }

    private boolean isLiked(UUID id, UUID userId) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(LIKE_SET + id, userId.toString()));
    }
}