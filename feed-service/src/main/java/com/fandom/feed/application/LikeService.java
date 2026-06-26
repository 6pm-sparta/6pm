package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.LikeResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {
    private final PostReader postReader;
    private final PostAssembler postAssembler;
    private final ReactionCacheService reactionCacheService;
    private final LikeRepository likeRepository;

    public LikeResponse createLike(UUID postId, UUID userId) {
        postReader.findById(postId);
        long likeCount = reactionCacheService.addLike(postId, userId);
        return LikeResponse.of(postId, likeCount);
    }

    public CursorPageResponse<PostResponse.Summary> getLikes(UUID cursor, ReactionSort sort, UUID userId) {
        List<Like> likes = likeRepository.findByCursorAndUserId(cursor, sort, userId);

        boolean hasMore = likes.size() > FeedPolicy.PAGE_SIZE;
        List<Like> page = hasMore ? likes.subList(0, FeedPolicy.PAGE_SIZE) : likes;

        List<UUID> orderedPostIds = page.stream().map(Like::getPostId).toList();

        Map<UUID, Post> postMap = postReader.findAllByIds(orderedPostIds)
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> orderedPosts = orderedPostIds.stream().map(postMap::get).filter(Objects::nonNull).toList();

        UUID nextCursor = hasMore ? page.getLast().getId() : null;
        return postAssembler.buildDBResponse(orderedPosts, nextCursor, hasMore, userId, true);
    }

    @Transactional
    public LikeResponse deleteLike(UUID postId, UUID userId) {
        postReader.findById(postId);
        likeRepository.deleteByPostIdAndUserId(postId, userId);
        long likeCount = reactionCacheService.removeLike(postId, userId);
        return LikeResponse.of(postId, likeCount);
    }

    /**
     * 사용자 ID로 모든 좋아요를 삭제하는 메서드
     */
    @Transactional
    public void deleteAllByUserId(UUID userId) {
        List<UUID> postIds = likeRepository.findPostIdsByUserId(userId);
        likeRepository.deleteAllByUserId(userId);
        reactionCacheService.removeLikeBatch(postIds, userId);
    }

    /**
     * 게시글 ID로 모든 좋아요를 삭제하는 메서드
     */
    @Transactional
    public void deleteAllByPostId(UUID postId) {
        deleteAllByPostIds(List.of(postId));
    }

    /**
     * 게시글 ID 목록으로 모든 좋아요를 삭제하는 메서드
     */
    @Transactional
    public void deleteAllByPostIds(List<UUID> postIds) {
        likeRepository.deleteAllByPostIdIn(postIds);
        reactionCacheService.deleteLikeSetBatch(postIds);
    }
}