package com.fandom.feed.infra.redis;

import com.fandom.common.exception.CustomException;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.exception.LikeErrorCode;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionCacheService {
    private final PostReader postReader;
    private final LikeRepository likeRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int COMMENT_COUNT_IDX = 0;
    private static final int LIKE_COUNT_IDX = 1;
    private static final int IS_LIKED_IDX = 2;

    @Value("${cache.ttl.comment-count}")
    private long commentCountTtl;

    /**
     * 게시글 ID로 캐시에서 게시글 리액션 정보를 조회하는 메서드
     */
    public ReactionInfoCache getReactionInfo(UUID postId, UUID userId) {
        return new ReactionInfoCache(getCommentCount(postId), getLikeCount(postId), isLiked(postId, userId));
    }

    /**
     * 게시글 ID 목록으로 캐시에서 게시글 리액션 정보를 조회하는 메서드
     */
    public List<ReactionInfoCache> getReactionInfoBatch(List<UUID> postIds, UUID userId, boolean isLiked) {
        // Redis Pipeline을 통해 여러 명령을 한 번의 네트워크 요청으로 처리
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            postIds.forEach(postId -> {
                String commentKey = RedisKeyPrefix.COMMENT_COUNT + postId;
                String likeKey = RedisKeyPrefix.LIKE_SET + postId;

                // 커맨드 순서: commentCount → likeCount → isLiked
                connection.stringCommands().get(commentKey.getBytes());
                connection.setCommands().sCard(likeKey.getBytes());

                if (!isLiked && (userId != null))
                    connection.setCommands().sIsMember(likeKey.getBytes(), userId.toString().getBytes());
            });
            return null;
        });

        // 좋아요 상태 조회 시 3개, 아니면 2개 묶음
        int step = (!isLiked && (userId != null)) ? 3 : 2;

        // 캐시 미스 ID 수집
        List<UUID> missedIds = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            int base = i * step;

            // 댓글 수가 null이면 좋아요 수도 없다고 가정
            if (results.get(base) == null)
                missedIds.add(postIds.get(i));
        }

        // DB 조회
        Map<UUID, long[]> dbResultMap = new HashMap<>();
        if (!missedIds.isEmpty())
            dbResultMap = fetchFromDbAndCache(missedIds);

        List<ReactionInfoCache> reactionInfos = new ArrayList<>();
        for (int i = 0; i < postIds.size(); i++) {
            int base = i * step;
            UUID postId = postIds.get(i);

            long commentCount;
            long likeCount;

            if (dbResultMap.containsKey(postId)) {
                long[] dbCounts = dbResultMap.get(postId);
                commentCount = dbCounts[COMMENT_COUNT_IDX];
                likeCount = dbCounts[LIKE_COUNT_IDX];
            } else {
                commentCount = (results.get(base + COMMENT_COUNT_IDX ) != null)
                        ? Long.parseLong(results.get(base + COMMENT_COUNT_IDX).toString()) : 0L;
                likeCount = (results.get(base + LIKE_COUNT_IDX) != null)
                        ? (Long) results.get(base + LIKE_COUNT_IDX) : 0L;
            }

            boolean liked = isLiked || ((userId != null) && Boolean.TRUE.equals(results.get(base + IS_LIKED_IDX)));
            reactionInfos.add(ReactionInfoCache.of(commentCount, likeCount, liked));
        }

        return reactionInfos;
    }

    /**
     * 게시글 ID로 캐시에서 댓글 수를 조회하는 메서드<br>
     * - 캐시 미스 발생 시, DB 조회 후 캐시에 저장
     */
    private long getCommentCount(UUID postId) {
        String key = RedisKeyPrefix.COMMENT_COUNT + postId;
        String count = redisTemplate.opsForValue().get(key);

        if (count == null) {
            Post post = postReader.findById(postId);
            long commentCount = post.getCommentCount();
            redisTemplate.opsForValue().set(key, String.valueOf(commentCount), commentCountTtl, TimeUnit.SECONDS);
            return commentCount;
        }
        return Long.parseLong(count);
    }

    /**
     * 캐시에 사용자 ID를 추가하는 메서드
     */
    public long addLike(UUID postId, UUID userId) {
        Long added = redisTemplate.opsForSet().add(RedisKeyPrefix.LIKE_SET + postId, userId.toString());

        if (added == null || added == 0L)
            throw new CustomException(LikeErrorCode.DUPLICATE_LIKE);

        return getLikeCountFromCache(postId);
    }

    /**
     * 게시글 ID로 캐시에서 좋아요 수를 조회하는 메서드<br>
     * - 캐시 미스 발생 시, DB 조회 후 캐시에 저장
     */
    private long getLikeCount(UUID postId) {
        String key = RedisKeyPrefix.LIKE_SET + postId;
        String commentKey = RedisKeyPrefix.COMMENT_COUNT + postId;
        Long size = redisTemplate.opsForSet().size(key);

        // 댓글 수가 null이면 좋아요 수도 없다고 가정
        if (redisTemplate.opsForValue().get(commentKey) == null) {
            List<String> userIds = likeRepository.findAllByPostId(postId)
                    .stream().map(like -> like.getUserId().toString()).toList();

            if (!userIds.isEmpty()) {
                redisTemplate.opsForSet().add(key, userIds.toArray(new String[0]));
                return userIds.size();
            }
            return 0L;
        }

        return (size != null) ? size : 0L;
    }

    /**
     * 게시글 ID로 캐시에서 좋아요 수를 조회하는 메서드
     */
    private long getLikeCountFromCache(UUID postId) {
        Long size = redisTemplate.opsForSet().size(RedisKeyPrefix.LIKE_SET + postId);
        return (size != null) ? size : 0L;
    }

    /**
     * 캐시에서 사용자 ID를 삭제하는 메서드
     */
    public long removeLike(UUID postId, UUID userId) {
        redisTemplate.opsForSet().remove(RedisKeyPrefix.LIKE_SET + postId, userId.toString());
        return getLikeCountFromCache(postId);
    }

    /**
     * 게시글 ID 목록으로 캐시에서 사용자 ID를 삭제하는 메서드
     */
    public void removeLikeBatch(List<UUID> postIds, UUID userId) {
        redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            postIds.forEach(postId ->
                    connection.setCommands().sRem(
                            (RedisKeyPrefix.LIKE_SET + postId).getBytes(),
                            userId.toString().getBytes()
                    )
            );
            return null;
        });
    }

    /**
     * 게시글 ID 목록으로 캐시에서 좋아요 Set을 삭제하는 메서드
     */
    public void deleteLikeSetBatch(List<UUID> postIds) {
        redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            postIds.forEach(postId ->
                    connection.keyCommands().del((RedisKeyPrefix.LIKE_SET + postId).getBytes())
            );
            return null;
        });
    }

    /**
     * 캐시에서 좋아요 상태를 조회하는 메서드
     */
    private boolean isLiked(UUID postId, UUID userId) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeyPrefix.LIKE_SET + postId, userId.toString()));
    }

    /**
     * DB에서 댓글 수와 좋아요 사용자를 조회한 뒤, 캐시에 저장하는 메서드
     */
    private Map<UUID, long[]> fetchFromDbAndCache(List<UUID> missedIds) {
        Map<UUID, Long> commentCounts = postReader.findAllByIds(missedIds)
                .stream().collect(Collectors.toMap(Post::getId, Post::getCommentCount));
        Map<UUID, List<UUID>> likeUserMap = likeRepository.findLikeUsersByPostIds(missedIds);

        Map<UUID, long[]> resultMap = new HashMap<>();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (UUID missedId : missedIds) {
                long commentCount = commentCounts.getOrDefault(missedId, 0L);
                List<UUID> likeUsers = likeUserMap.getOrDefault(missedId, List.of());

                String commentKey = RedisKeyPrefix.COMMENT_COUNT + missedId;
                String likeKey = RedisKeyPrefix.LIKE_SET + missedId;

                connection.stringCommands().setEx(
                        commentKey.getBytes(),
                        commentCountTtl,
                        String.valueOf(commentCount).getBytes()
                );

                // Set 복원 후 TTL 설정
                if (!likeUsers.isEmpty()) {
                    byte[][] memberBytes = likeUsers.stream()
                            .map(uid -> uid.toString().getBytes())
                            .toArray(byte[][]::new);
                    connection.setCommands().sAdd(likeKey.getBytes(), memberBytes);
                }

                resultMap.put(missedId, new long[]{commentCount, (long) likeUsers.size()});
            }
            return null;
        });

        return resultMap;
    }
}