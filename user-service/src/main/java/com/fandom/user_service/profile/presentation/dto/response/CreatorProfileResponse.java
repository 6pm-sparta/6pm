package com.fandom.user_service.profile.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.time.LocalDate;
import java.util.UUID;

public record CreatorProfileResponse(
        UUID userId,
        String nickname,
        LocalDate birthday,
        String profileMessage,
        String profileImage,
        String bannerImage,
        int followerCount,
        int followingCount,
        String agencyName
) {

    public static CreatorProfileResponse from(User user, Profile profile, Creator creator) {
        return new CreatorProfileResponse(
                user.getId(),
                profile.getNickname(),
                profile.getBirthday(),
                profile.getProfileMessage(),
                profile.getProfileImage(),
                profile.getBannerImage(),
                profile.getFollowerCount(),
                profile.getFollowingCount(),
                creator.getAgencyName()
        );
    }
}
