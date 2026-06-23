package com.fandom.feed.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_user", columnNames = {"post_id", "user_id"}),
        indexes = @Index(name = "idx_likes_user_id", columnList = "user_id")
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Like extends BaseEntity {
    @Column(nullable = false)
    private UUID postId;

    @Column(nullable = false)
    private UUID userId;

    @Builder
    private Like(UUID postId, UUID userId) {
        this.postId = postId;
        this.userId = userId;
    }
}