package com.fandom.feed.presentation.dto.response;

import com.fandom.feed.domain.entity.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PostResponse {
    public record Create(UUID postId, String content, List<String> imageUrls, LocalDateTime createdAt) {
        public static Create of(Post post, List<String> imageUrls) {
            return new Create(post.getId(), post.getContent(), imageUrls, post.getCreatedAt());
        }
    }
}