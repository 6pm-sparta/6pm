package com.fandom.user_service.follow.domain.entity;

import com.fandom.common.entity.BaseEntity;
import com.fandom.user_service.member.domain.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
public class Follow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @Builder
    private Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
    }
}
