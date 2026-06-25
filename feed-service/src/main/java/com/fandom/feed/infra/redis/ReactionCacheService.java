package com.fandom.feed.infra.redis;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.domain.exception.LikeErrorCode;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostCache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionCacheService {
    private final LikeRepository likeRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cache.ttl.comment-count}")
    private long commentCountTtl;

    /**
     * 게시글 ID로 캐시에서 게시글 리액션 정보를 조회하는 메서드<br>
     * TODO: Comment 도메인 연동 필요 [P1]
     */
    public PostCache.ReactionInfo getReactionInfo(UUID postId, UUID userId) {
        return new PostCache.ReactionInfo(getCommentCount(postId), getLikeCount(postId), isLiked(postId, userId));
    }

    /**
     * 게시글 ID로 캐시에서 게시글 리액션 정보를 배치 조회하는 메서드<br>
     * TODO: Comment, Like 도메인 연동 필요 [P1]
     */
    public List<PostCache.ReactionInfo> getReactionInfoBatch(List<UUID> ids, UUID userId, boolean isLiked) {
        // Redis Pipeline을 통해 여러 명령을 한 번의 네트워크 요청으로 처리
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            ids.forEach(id -> {
                String commentKey = RedisKeyPrefix.COMMENT_COUNT + id;
                String likeKey = RedisKeyPrefix.LIKE_SET + id;

                // 커맨드 순서: commentCount → likeCount → isLiked
                connection.stringCommands().get(commentKey.getBytes());
                connection.setCommands().sCard(likeKey.getBytes());

                if (!isLiked && (userId != null))
                    connection.setCommands().sIsMember(likeKey.getBytes(), userId.toString().getBytes());
            });
            return null;
        });

        // 사용자 식별자 있으면 3개, 없으면 2개 묶음
        int step = (userId != null) ? 3 : 2;
        List<PostCache.ReactionInfo> reactionInfos = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            int base = i * step;

            long commentCount = (results.get(base) != null) ? Long.parseLong(results.get(base).toString()) : 0L;
            long likeCount = (results.get(base + 1) != null) ? (Long) results.get(base + 1) : 0L;
            boolean liked = isLiked || ((userId != null) && Boolean.TRUE.equals(results.get(base + 2)));

            reactionInfos.add(new PostCache.ReactionInfo(commentCount, likeCount, liked));
        }

        return reactionInfos;
    }

    private long getCommentCount(UUID id) {
        String count = redisTemplate.opsForValue().get(RedisKeyPrefix.COMMENT_COUNT + id);
        return (count != null) ? Long.parseLong(count) : 0L;
    }

    /**
     * 캐시에 사용자 ID를 추가하는 메서드
     */
    public long addLike(UUID postId, UUID userId) {
        Long added = redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId, userId.toString());

        if (added == null || added == 0L)
            throw new CustomException(LikeErrorCode.DUPLICATE_LIKE);

        return getLikeCount(postId);
    }

    /**
     * 게시글 ID로 캐시에서 좋아요 수를 조회하는 메서드<br>
     * - 캐시 미스 발생 시, DB 조회 후 캐시에 저장 (좋아요 0개 포함)
     */
    private long getLikeCount(UUID postId) {
        String key = RedisKeyPrefix.LIKE_SET + postId;
        Long size = redisTemplate.opsForSet().size(key);

        // 캐시 미스 조회
        if (size == null || size == 0L) {
            List<String> userIds = likeRepository.findAllByPostId(postId)
                    .stream().map(like -> like.getUserId().toString()).toList();

            if (!userIds.isEmpty()) {
                redisTemplate.opsForSet().add(key, userIds.toArray(new String[0]));
                return userIds.size();
            }
            return 0L;
        }

        return size;
    }

    /**
     * 캐시에 사용자 ID를 삭제하는 메서드
     */
    public long removeLike(UUID postId, UUID userId) {
        redisTemplate.opsForSet().remove(RedisKeyPrefix.LIKE_SET + postId, userId.toString());
        return getLikeCount(postId);
    }

    /**
     * 캐시에서 좋아요 상태를 조회하는 메서드
     */
    private boolean isLiked(UUID id, UUID userId) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + id, userId.toString()));
    }
}