package com.fandom.feed.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "images",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_order", columnNames = {"postId", "order"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image extends BaseEntity {
    @Column(nullable = false)
    private UUID postId;

    @Column(nullable = false)
    private Integer order;

    @Column(nullable = false)
    private String imageKey;

    @Builder
    public Image(UUID postId, Integer order, String imageKey) {
        this.postId = postId;
        this.order = order;
        this.imageKey = imageKey;
    }
}