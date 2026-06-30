package com.fandom.feed.infra.redis.dto;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PostDetailCache(
        UUID postId, UserResponse author, String content, List<String> imageUrls,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static PostDetailCache of(Post post, List<String> imageUrls, UserResponse author) {
        return new PostDetailCache(
                post.getId(), author, post.getContent(), imageUrls, post.getCreatedAt(), post.getUpdatedAt()
        );
    }
}