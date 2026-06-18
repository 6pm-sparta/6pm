package com.fandom.feed.presentation.dto.response;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PostResponse {
    public record Create(UUID postId, String content, List<String> imageUrls, LocalDateTime createdAt) {
        public static Create of(Post post, List<String> imageUrls) {
            return new Create(post.getId(), post.getContent(), imageUrls, post.getCreatedAt());
        }
    }

    public record Detail(
            UUID postId, UserResponse author, String content, List<String> imageUrls,
            long commentCount, long likeCount, boolean liked, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Detail of(PostCache.Detail cachedPost, long commentCount) {
            return new Detail(
                    cachedPost.postId(), cachedPost.author(), cachedPost.content(), cachedPost.imageUrls(),
                    commentCount, 0L, false, cachedPost.createdAt(), cachedPost.updatedAt()
            );
        }

        public Detail withReaction(PostCache.ReactionInfo reactionInfo) {
            return new Detail(
                    this.postId, this.author, this.content, this.imageUrls,
                    reactionInfo.commentCount(), reactionInfo.likeCount(), reactionInfo.liked(),
                    this.createdAt, this.updatedAt
            );
        }
    }
}