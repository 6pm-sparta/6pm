package com.fandom.user_service.follow.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.util.UUID;

public record FollowingResponse(
        UUID userId,
        String nickname,
        String profileImage,
        int followerCount
) {

    public static FollowingResponse from(User user, Profile profile) {
        return new FollowingResponse(
                user.getId(),
                profile.getNickname(),
                profile.getProfileImage(),
                profile.getFollowerCount()
        );
    }
}
