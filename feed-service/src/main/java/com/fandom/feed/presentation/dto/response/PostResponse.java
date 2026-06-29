package com.fandom.feed.presentation.dto.response;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostDetailCache;
import com.fandom.feed.infra.redis.dto.ReactionInfoCache;

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
        public static Detail of(PostDetailCache cachedPost, ReactionInfoCache reactionInfo) {
            return new Detail(
                    cachedPost.postId(), cachedPost.author(), cachedPost.content(), cachedPost.imageUrls(),
                    reactionInfo.commentCount(), reactionInfo.likeCount(), reactionInfo.liked(),
                    cachedPost.createdAt(), cachedPost.updatedAt()
            );
        }
    }

    public record Summary(
            UUID postId, UserResponse author, String content, boolean hasMore, String imageUrl,
            int imageCount, long commentCount, long likeCount, boolean liked, LocalDateTime createdAt
    ) {
        public static Summary of(PostDetailCache cachedPost, ReactionInfoCache reactionInfo) {
            List<String> imageUrls = cachedPost.imageUrls();
            String content = cachedPost.content();

            boolean hasMore = content != null && content.length() > 150;
            String summaryContent = hasMore ? content.substring(0, 150) : content;

            return new Summary(
                    cachedPost.postId(), cachedPost.author(), summaryContent, hasMore,
                    imageUrls.isEmpty() ? null : imageUrls.getFirst(), imageUrls.size(),
                    reactionInfo.commentCount(), reactionInfo.likeCount(), reactionInfo.liked(), cachedPost.createdAt()
            );
        }
    }

    public record Update(UUID postId, String content, List<String> imageUrls, LocalDateTime updatedAt) {
        public static Update of(Post post, List<String> imageUrls) {
            return new Update(post.getId(), post.getContent(), imageUrls, post.getUpdatedAt());
        }
    }

    public record Delete(UUID postId, LocalDateTime deletedAt) {
        public static Delete from(Post post) {
            return new Delete(post.getId(), post.getDeletedAt());
        }
    }
}