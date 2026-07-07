package com.fandom.user_service.follow.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.util.UUID;

public record FollowerResponse(
        UUID userId,
        String nickname,
        String profileImage
) {

    public static FollowerResponse from(User user, Profile profile) {
        return new FollowerResponse(
                user.getId(),
                profile.getNickname(),
                profile.getProfileImage()
        );
    }
}
