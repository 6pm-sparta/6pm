package com.fandom.user_service.follow.domain.entity;

import com.fasterxml.uuid.Generators;
import com.fandom.user_service.member.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "follows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_follows_follower_followee",
                        columnNames = {"follower_id", "followee_id"}
                )
        }
)
public class Follow {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
    }

    @PrePersist
    void assignIdAndCreatedAt() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
