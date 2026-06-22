package com.fandom.user_service.follow.presentation.dto.response;

import com.fandom.user_service.follow.domain.entity.Follow;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowResponse(
        UUID followId,
        UUID followerId,
        UUID followeeId,
        LocalDateTime createdAt
) {

    public static FollowResponse from(Follow follow) {
        return new FollowResponse(
                follow.getId(),
                follow.getFollower().getId(),
                follow.getFollowee().getId(),
                follow.getCreatedAt()
        );
    }
}
