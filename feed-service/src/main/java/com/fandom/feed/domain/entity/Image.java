package com.fandom.feed.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Entity
@Table(
        name = "images",
        uniqueConstraints = @UniqueConstraint(name = "uq_posts_post_order_index", columnNames = {"post_id", "order_index"})
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image extends SimpleBaseEntity {
    @Column(nullable = false)
    private UUID postId;

    @Check(constraints = "order_index BETWEEN 0 AND 3")
    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, unique = true)
    private String imageKey;

    @Builder
    private Image(UUID postId, Integer orderIndex, String imageKey) {
        this.postId = postId;
        this.orderIndex = orderIndex;
        this.imageKey = imageKey;
    }
}