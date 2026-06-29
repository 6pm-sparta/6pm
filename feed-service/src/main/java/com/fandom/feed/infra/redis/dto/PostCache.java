package com.fandom.feed.infra.redis.dto;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.client.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PostCache {
    public record Detail(
            UUID postId, UserResponse author, String content, List<String> imageUrls,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Detail of(Post post, List<String> imageUrls, UserResponse author) {
            return new Detail(
                    post.getId(), author, post.getContent(), imageUrls, post.getCreatedAt(), post.getUpdatedAt()
            );
        }
    }

    public record ReactionInfo(long commentCount, long likeCount, boolean liked) {
        public static ReactionInfo of(long commentCount, long likeCount, boolean liked) {
            return new ReactionInfo(commentCount, likeCount, liked);
        }
    }
}