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
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_post_id", columnList = "post_id, id DESC"),
                @Index(name = "idx_comments_author_id", columnList = "author_id, id DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Comment extends BaseEntity {
    @Column(nullable = false)
    private UUID postId;

    @Column
    private UUID authorId;

    @Column(nullable = false)
    private String content;

    @Builder
    private Comment(UUID postId, UUID authorId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
    }

    public void update(String content) {
        this.content = content;
    }
}