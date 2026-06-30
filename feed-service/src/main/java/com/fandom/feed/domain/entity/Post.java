package com.fandom.feed.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(
        name = "posts",
        indexes = @Index(name = "idx_posts_author_id", columnList = "author_id, id DESC")
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Post extends BaseEntity {
    @Column(nullable = false)
    private UUID authorId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private long commentCount = 0L;

    @Builder
    private Post(UUID authorId, String content) {
        this.authorId = authorId;
        this.content = content;
    }

    public void update(String content) {
        this.content = content;
    }
}