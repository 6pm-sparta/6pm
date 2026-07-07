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
import com.fandom.user_service.member.presentation.dto.response.InternalUserResponse;
import com.fandom.user_service.profile.presentation.dto.response.CreatorProfileResponse;
import com.fandom.user_service.profile.presentation.dto.response.MemberProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
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

    /**
     * 내부 사용자 단건 조회. (feed-service 등 서비스 간 통신 전용)
     * 탈퇴(soft-delete)한 회원은 조회되지 않으며, 없으면 404를 던진다.
     */
    public InternalUserResponse getUserForInternal(UUID userId) {
        Profile profile = profileRepository.findByUserId(userId)
                .filter(p -> !p.getUser().isWithdrawn())
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));

        return InternalUserResponse.from(profile);
    }

    /**
     * 내부 사용자 배치 조회. (feed-service 등 서비스 간 통신 전용)
     * 살아있는 회원만 반환하며, 없거나 탈퇴한 userId는 결과에서 제외한다.
     * (호출 측이 누락분을 "탈퇴한 사용자" 등으로 보완한다)
     */
    public List<InternalUserResponse> getUsersForInternal(Set<UUID> userIds) {
        return profileRepository.findAllByUserIdIn(userIds).stream()
                .filter(p -> !p.getUser().isWithdrawn())
                .map(InternalUserResponse::from)
                .toList();
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
