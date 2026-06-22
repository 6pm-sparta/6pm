package com.fandom.user_service.member.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.Status;
import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.member.application.port.MemberWithdrawalEventPublisher;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.CreatorUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.MemberUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.CreatorSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.CreatorUpdateResponse;
import com.fandom.user_service.member.presentation.dto.response.InternalMemberResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberUpdateResponse;
import com.fandom.user_service.profile.application.ProfileService;
import com.fandom.user_service.profile.domain.entity.Profile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProfileService profileService;

    @Mock
    private MemberWithdrawalEventPublisher memberWithdrawalEventPublisher;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("일반회원 가입에 성공하면 User와 Profile이 저장되고 응답이 반환된다")
    void signUp_success() {
        // given
        SignUpRequest request = new SignUpRequest(
                "test@example.com",
                "password123",
                "테스터",
                "06978",
                "서울특별시 동작구 상도로 369",
                "단독"
        );
        Profile profile = Profile.builder()
                .nickname(request.nickname())
                .build();
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(profileService.createInitialProfile(any(User.class), anyString())).willReturn(profile);

        // when
        MemberSignUpResponse response = memberService.signUp(request);

        // then
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("테스터");
        assertThat(response.role()).isEqualTo(Role.MEMBER.name());
        assertThat(response.status()).isEqualTo(Status.ACTIVE.name());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(profileService).createInitialProfile(any(User.class), anyString());
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 가입하면 DUPLICATE_EMAIL 예외가 발생한다")
    void signUp_duplicateEmail() {
        // given
        SignUpRequest request = new SignUpRequest(
                "dup@example.com",
                "password123",
                "중복닉네임",
                null,
                null,
                null
        );
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        // 중복이면 저장/해싱까지 가지 않는다
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(profileService, never()).createInitialProfile(any(User.class), anyString());
    }

    @Test
    @DisplayName("크리에이터 가입에 성공하면 User, Creator, Profile이 저장되고 응답이 반환된다")
    void signUpCreator_success() {
        // given
        CreatorSignUpRequest request =
                new CreatorSignUpRequest(
                        "creator@example.com",
                        "password123",
                        "크리에이터",
                        "소속사",
                        "12028",
                        "경기도 남양주시 오남읍 진건오남로667번길 64-33",
                        null
                );
        Profile profile = Profile.builder()
                .nickname(request.nickname())
                .build();
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(creatorRepository.save(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(profileService.createInitialProfile(any(User.class), anyString())).willReturn(profile);

        // when
        CreatorSignUpResponse response = memberService.signUpCreator(request);

        // then
        assertThat(response.email()).isEqualTo("creator@example.com");
        assertThat(response.nickname()).isEqualTo("크리에이터");
        assertThat(response.agencyName()).isEqualTo("소속사");
        assertThat(response.role()).isEqualTo(Role.CREATOR.name());
        assertThat(response.status()).isEqualTo(Status.ACTIVE.name());
        verify(userRepository).save(any(User.class));
        verify(creatorRepository).save(any());
        verify(profileService).createInitialProfile(any(User.class), anyString());
    }

    @Test
    @DisplayName("일반회원 정보 수정에 성공하면 이메일, 비밀번호, 주소가 변경된다")
    void updateMember_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("old@example.com")
                .password("old-password")
                .role(Role.MEMBER)
                .build();
        MemberUpdateRequest request = new MemberUpdateRequest(
                "newPassword123!",
                "new@example.com",
                "06235",
                "서울특별시 강남구 역삼로 100",
                "101호"
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByEmailAndIdNot(request.email(), user.getId())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-new-password");

        // when
        MemberUpdateResponse response = memberService.updateMember(userId, request);

        // then
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(user.getZipCode()).isEqualTo("06235");
        verify(passwordEncoder).encode("newPassword123!");
    }

    @Test
    @DisplayName("일반회원 정보 수정 시 기존 이메일과 같으면 중복 검사를 하지 않는다")
    void updateMember_sameEmail() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("same@example.com")
                .password("old-password")
                .role(Role.MEMBER)
                .build();
        MemberUpdateRequest request = new MemberUpdateRequest(null, "same@example.com", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        MemberUpdateResponse response = memberService.updateMember(userId, request);

        // then
        assertThat(response.email()).isEqualTo("same@example.com");
        verify(userRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
    }

    @Test
    @DisplayName("일반회원 정보 수정 시 이메일이 중복되면 DUPLICATE_EMAIL 예외가 발생한다")
    void updateMember_duplicateEmail() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("old@example.com")
                .password("old-password")
                .role(Role.MEMBER)
                .build();
        MemberUpdateRequest request = new MemberUpdateRequest(null, "dup@example.com", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByEmailAndIdNot(request.email(), user.getId())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.updateMember(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("일반회원 정보 수정 API에 크리에이터가 접근하면 FORBIDDEN_MEMBER_ACCESS 예외가 발생한다")
    void updateMember_forbiddenRole() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("creator@example.com")
                .password("password")
                .role(Role.CREATOR)
                .build();
        MemberUpdateRequest request = new MemberUpdateRequest(null, "new@example.com", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> memberService.updateMember(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.FORBIDDEN_MEMBER_ACCESS);
    }

    @Test
    @DisplayName("크리에이터 정보 수정에 성공하면 이메일, 비밀번호, 소속사명이 변경된다")
    void updateCreator_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("old-creator@example.com")
                .password("old-password")
                .role(Role.CREATOR)
                .build();
        Creator creator = Creator.builder()
                .user(user)
                .agencyName("기존소속사")
                .build();
        CreatorUpdateRequest request = new CreatorUpdateRequest(
                "newPassword123!",
                "new-creator@example.com",
                "새소속사",
                null,
                null,
                null
        );

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(creatorRepository.findByUserId(userId)).willReturn(Optional.of(creator));
        given(userRepository.existsByEmailAndIdNot(request.email(), user.getId())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-new-password");

        // when
        CreatorUpdateResponse response = memberService.updateCreator(userId, request);

        // then
        assertThat(response.email()).isEqualTo("new-creator@example.com");
        assertThat(response.agencyName()).isEqualTo("새소속사");
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        assertThat(creator.getAgencyName()).isEqualTo("새소속사");
    }

    @Test
    @DisplayName("크리에이터 정보 수정 API에 일반회원이 접근하면 FORBIDDEN_MEMBER_ACCESS 예외가 발생한다")
    void updateCreator_forbiddenRole() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("member@example.com")
                .password("password")
                .role(Role.MEMBER)
                .build();
        CreatorUpdateRequest request = new CreatorUpdateRequest(null, null, "소속사", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> memberService.updateCreator(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.FORBIDDEN_MEMBER_ACCESS);

        verify(creatorRepository, never()).findByUserId(any(UUID.class));
    }

    @Test
    @DisplayName("크리에이터 정보 수정 시 Creator 정보가 없으면 CREATOR_NOT_FOUND 예외가 발생한다")
    void updateCreator_notFound() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("creator@example.com")
                .password("password")
                .role(Role.CREATOR)
                .build();
        CreatorUpdateRequest request = new CreatorUpdateRequest(null, null, "소속사", null, null, null);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(creatorRepository.findByUserId(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.updateCreator(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.CREATOR_NOT_FOUND);
    }

    @Test
    @DisplayName("일반회원 탈퇴에 성공하면 상태를 DELETED로 바꾸고 탈퇴 이벤트를 발행한다")
    void withdraw_member_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("member@example.com")
                .password("password")
                .role(Role.MEMBER)
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        memberService.withdraw(userId);

        // then
        assertThat(user.getStatus()).isEqualTo(Status.DELETED);
        assertThat(user.isDeleted()).isTrue();
        verify(memberWithdrawalEventPublisher).publish(userId, Role.MEMBER);
    }

    @Test
    @DisplayName("크리에이터 탈퇴에 성공하면 상태를 DELETED로 바꾸고 크리에이터 탈퇴 이벤트를 발행한다")
    void withdraw_creator_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("creator@example.com")
                .password("password")
                .role(Role.CREATOR)
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        memberService.withdraw(userId);

        // then
        assertThat(user.getStatus()).isEqualTo(Status.DELETED);
        assertThat(user.isDeleted()).isTrue();
        verify(memberWithdrawalEventPublisher).publish(userId, Role.CREATOR);
    }

    @Test
    @DisplayName("이미 탈퇴된 회원의 탈퇴 요청은 멱등하게 성공 처리하고 이벤트를 재발행하지 않는다")
    void withdraw_alreadyWithdrawn_idempotent() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("deleted@example.com")
                .password("password")
                .role(Role.MEMBER)
                .build();
        user.withdraw(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        memberService.withdraw(userId);

        // then
        verify(memberWithdrawalEventPublisher, never()).publish(any(UUID.class), any(Role.class));
    }

    @Test
    @DisplayName("내부 조회 시 존재하는 이메일이면 비밀번호 해시를 포함한 정보를 반환한다")
    void findByEmailForInternal_success() {
        // given
        User user = User.builder()
                .email("test@example.com")
                .password("encoded-password")
                .role(Role.MEMBER)
                .build();
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        // when
        InternalMemberResponse response = memberService.findByEmailForInternal("test@example.com");

        // then
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.password()).isEqualTo("encoded-password");
        assertThat(response.role()).isEqualTo(Role.MEMBER.name());
    }

    @Test
    @DisplayName("내부 조회 시 존재하지 않는 이메일이면 MEMBER_NOT_FOUND 예외가 발생한다")
    void findByEmailForInternal_notFound() {
        // given
        given(userRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.findByEmailForInternal("none@example.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }
}
