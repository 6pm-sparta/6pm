package com.fandom.user_service.profile.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;
import com.fandom.user_service.profile.domain.exception.ProfileErrorCode;
import com.fandom.user_service.profile.domain.repository.ProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService 단위 테스트")
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    @DisplayName("초기 프로필 생성에 성공하면 닉네임과 팔로우 기본값이 저장된다")
    void createInitialProfile_success() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .role(Role.MEMBER)
                .build();
        given(profileRepository.existsByNickname("테스터")).willReturn(false);
        given(profileRepository.save(any(Profile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Profile profile = profileService.createInitialProfile(user, "테스터");

        // then
        assertThat(profile.getUser()).isEqualTo(user);
        assertThat(profile.getNickname()).isEqualTo("테스터");
        assertThat(profile.getFollowerCount()).isZero();
        assertThat(profile.getFollowingCount()).isZero();
        verify(profileRepository).save(any(Profile.class));
    }

    @Test
    @DisplayName("이미 존재하는 닉네임이면 DUPLICATE_NICKNAME 예외가 발생한다")
    void createInitialProfile_duplicateNickname() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .role(Role.MEMBER)
                .build();
        given(profileRepository.existsByNickname("중복닉네임")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> profileService.createInitialProfile(user, "중복닉네임"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ProfileErrorCode.DUPLICATE_NICKNAME);

        verify(profileRepository, never()).save(any(Profile.class));
    }
}
