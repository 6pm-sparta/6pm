package com.fandom.user_service.profile.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.time.LocalDate;
import java.util.UUID;

public record MemberProfileResponse(
        UUID userId,
        String nickname,
        LocalDate birthday,
        String profileMessage,
        String profileImage,
        String bannerImage,
        int followerCount,
        int followingCount
) {

    public static MemberProfileResponse from(User user, Profile profile) {
        return new MemberProfileResponse(
                user.getId(),
                profile.getNickname(),
                profile.getBirthday(),
                profile.getProfileMessage(),
                profile.getProfileImage(),
                profile.getBannerImage(),
                profile.getFollowerCount(),
                profile.getFollowingCount()
        );
    }
}
