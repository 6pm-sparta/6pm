package com.fandom.user_service.profile.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.exception.ProfileErrorCode;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import com.fandom.user_service.profile.presentation.dto.request.CreatorProfileUpdateRequest;
import com.fandom.user_service.profile.presentation.dto.request.MemberProfileUpdateRequest;
import com.fandom.user_service.profile.presentation.dto.response.CreatorProfileResponse;
import com.fandom.user_service.profile.presentation.dto.response.MemberProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;

    @Transactional
    public Profile createInitialProfile(User user, String nickname) {
        validateNicknameNotDuplicated(nickname);

        return profileRepository.save(
                Profile.builder()
                        .user(user)
                        .nickname(nickname)
                        .build()
        );
    }

    public MemberProfileResponse getMemberProfile(UUID userId) {
        User user = findUserById(userId);
        validateRole(user, Role.MEMBER);
        Profile profile = findProfileByUserId(userId);

        return MemberProfileResponse.from(user, profile);
    }

    public CreatorProfileResponse getCreatorProfile(UUID userId) {
        User user = findUserById(userId);
        validateRole(user, Role.CREATOR);
        Creator creator = findCreatorByUserId(userId);
        Profile profile = findProfileByUserId(userId);

        return CreatorProfileResponse.from(user, profile, creator);
    }

    @Transactional
    public MemberProfileResponse updateMemberProfile(UUID userId, MemberProfileUpdateRequest request) {
        User user = findUserById(userId);
        validateRole(user, Role.MEMBER);
        Profile profile = findProfileByUserId(userId);

        validateNicknameForUpdate(profile, request.nickname());
        profile.updateWithoutBirthday(
                request.nickname(),
                request.profileMessage(),
                request.profileImage(),
                request.bannerImage()
        );

        return MemberProfileResponse.from(user, profile);
    }

    @Transactional
    public CreatorProfileResponse updateCreatorProfile(UUID userId, CreatorProfileUpdateRequest request) {
        User user = findUserById(userId);
        validateRole(user, Role.CREATOR);
        Creator creator = findCreatorByUserId(userId);
        Profile profile = findProfileByUserId(userId);

        validateNicknameForUpdate(profile, request.nickname());
        profile.update(
                request.nickname(),
                request.birthday(),
                request.profileMessage(),
                request.profileImage(),
                request.bannerImage()
        );

        return CreatorProfileResponse.from(user, profile, creator);
    }

    private void validateNicknameNotDuplicated(String nickname) {
        if (profileRepository.existsByNickname(nickname)) {
            throw new CustomException(ProfileErrorCode.DUPLICATE_NICKNAME);
        }
    }

    private void validateNicknameForUpdate(Profile profile, String nickname) {
        if (nickname == null || nickname.equals(profile.getNickname())) {
            return;
        }
        if (profileRepository.existsByNicknameAndIdNot(nickname, profile.getId())) {
            throw new CustomException(ProfileErrorCode.DUPLICATE_NICKNAME);
        }
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private Creator findCreatorByUserId(UUID userId) {
        return creatorRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.CREATOR_NOT_FOUND));
    }

    private Profile findProfileByUserId(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    private void validateRole(User user, Role role) {
        if (user.getRole() != role) {
            throw new CustomException(MemberErrorCode.FORBIDDEN_MEMBER_ACCESS);
        }
    }
}
