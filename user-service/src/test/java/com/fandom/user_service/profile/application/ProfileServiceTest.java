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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 서비스 단위 테스트")
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorRepository creatorRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    @DisplayName("초기 프로필 생성 시 기본 프로필 값을 저장한다")
    void createInitialProfile_success() {
        User user = member();
        given(profileRepository.existsByNickname("tester")).willReturn(false);
        given(profileRepository.save(any(Profile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Profile profile = profileService.createInitialProfile(user, "tester");

        assertThat(profile.getUser()).isEqualTo(user);
        assertThat(profile.getNickname()).isEqualTo("tester");
        assertThat(profile.getFollowerCount()).isZero();
        assertThat(profile.getFollowingCount()).isZero();
        verify(profileRepository).save(any(Profile.class));
    }

    @Test
    @DisplayName("초기 프로필 생성 시 중복 닉네임이면 예외가 발생한다")
    void createInitialProfile_duplicateNickname() {
        User user = member();
        given(profileRepository.existsByNickname("duplicate")).willReturn(true);

        assertThatThrownBy(() -> profileService.createInitialProfile(user, "duplicate"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ProfileErrorCode.DUPLICATE_NICKNAME);

        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    @DisplayName("일반 회원 공개 프로필을 조회한다")
    void getMemberProfile_success() {
        UUID userId = UUID.randomUUID();
        User user = member();
        Profile profile = profile(user, "member");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        MemberProfileResponse response = profileService.getMemberProfile(userId);

        assertThat(response.nickname()).isEqualTo("member");
        assertThat(response.followerCount()).isZero();
        assertThat(response.followingCount()).isZero();
    }

    @Test
    @DisplayName("크리에이터 공개 프로필을 조회한다")
    void getCreatorProfile_success() {
        UUID userId = UUID.randomUUID();
        User user = creatorUser();
        Profile profile = profile(user, "creator");
        Creator creator = Creator.builder()
                .user(user)
                .agencyName("agency")
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(creatorRepository.findByUserId(userId)).willReturn(Optional.of(creator));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        CreatorProfileResponse response = profileService.getCreatorProfile(userId);

        assertThat(response.nickname()).isEqualTo("creator");
        assertThat(response.agencyName()).isEqualTo("agency");
    }

    @Test
    @DisplayName("일반 회원 프로필 수정 시 요청된 필드만 변경한다")
    void updateMemberProfile_success() {
        UUID userId = UUID.randomUUID();
        User user = member();
        Profile profile = profile(user, "old");
        MemberProfileUpdateRequest request =
                new MemberProfileUpdateRequest("new", "hello", null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(profileRepository.existsByNicknameAndIdNot("new", profile.getId())).willReturn(false);

        MemberProfileResponse response = profileService.updateMemberProfile(userId, request);

        assertThat(response.nickname()).isEqualTo("new");
        assertThat(response.profileMessage()).isEqualTo("hello");
        assertThat(profile.getNickname()).isEqualTo("new");
        assertThat(profile.getProfileMessage()).isEqualTo("hello");
    }

    @Test
    @DisplayName("크리에이터 프로필 수정 시 생일과 공개 프로필 필드를 변경한다")
    void updateCreatorProfile_success() {
        UUID userId = UUID.randomUUID();
        User user = creatorUser();
        Profile profile = profile(user, "old");
        Creator creator = Creator.builder()
                .user(user)
                .agencyName("agency")
                .build();
        LocalDate birthday = LocalDate.of(1994, 5, 22);
        CreatorProfileUpdateRequest request =
                new CreatorProfileUpdateRequest(null, birthday, "hello", null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(creatorRepository.findByUserId(userId)).willReturn(Optional.of(creator));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        CreatorProfileResponse response = profileService.updateCreatorProfile(userId, request);

        assertThat(response.birthday()).isEqualTo(birthday);
        assertThat(response.profileMessage()).isEqualTo("hello");
        assertThat(profile.getBirthday()).isEqualTo(birthday);
    }

    @Test
    @DisplayName("일반 회원 프로필 엔드포인트는 크리에이터 역할 접근을 거부한다")
    void getMemberProfile_forbiddenRole() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.of(creatorUser()));

        assertThatThrownBy(() -> profileService.getMemberProfile(userId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.FORBIDDEN_MEMBER_ACCESS);
    }

    @Test
    @DisplayName("프로필 수정 시 중복 닉네임이면 예외가 발생한다")
    void updateMemberProfile_duplicateNickname() {
        UUID userId = UUID.randomUUID();
        User user = member();
        Profile profile = profile(user, "old");
        MemberProfileUpdateRequest request =
                new MemberProfileUpdateRequest("duplicate", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(profileRepository.existsByNicknameAndIdNot("duplicate", profile.getId())).willReturn(true);

        assertThatThrownBy(() -> profileService.updateMemberProfile(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ProfileErrorCode.DUPLICATE_NICKNAME);
    }

    private User member() {
        return User.builder()
                .email("member@example.com")
                .password("encoded-password")
                .role(Role.MEMBER)
                .build();
    }

    private User creatorUser() {
        return User.builder()
                .email("creator@example.com")
                .password("encoded-password")
                .role(Role.CREATOR)
                .build();
    }

    private Profile profile(User user, String nickname) {
        return Profile.builder()
                .user(user)
                .nickname(nickname)
                .build();
    }
}
