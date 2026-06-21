package com.fandom.feed.presentation.dto.response;

import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.infra.client.dto.UserResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public class CommentResponse {
    public record Create(UUID commentId, String content, LocalDateTime createdAt) {
        public static Create from(Comment comment) {
            return new Create(comment.getId(), comment.getContent(), comment.getCreatedAt());
        }
    }

    public record Detail(
            UUID commentId, UserResponse author, String content, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Detail of(Comment comment, UserResponse author) {
            return new Detail(
                    comment.getId(), author, comment.getContent(), comment.getCreatedAt(), comment.getUpdatedAt()
            );
        }
    }

    public record Update(UUID commentId, String content, LocalDateTime updatedAt) {
        public static Update from(Comment comment) {
            return new Update(comment.getId(), comment.getContent(), comment.getUpdatedAt());
        }
    }

    public record Delete(UUID commentId, LocalDateTime deletedAt) {
        public static Delete from(Comment comment) {
            return new Delete(comment.getId(), comment.getDeletedAt());
        }
    }
}