package com.fandom.user_service.member.application;

import com.fandom.common.exception.CustomException;
import com.fandom.user_service.member.domain.entity.Role;
import com.fandom.user_service.member.domain.entity.Status;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.member.domain.exception.MemberErrorCode;
import com.fandom.user_service.member.domain.repository.CreatorRepository;
import com.fandom.user_service.member.domain.repository.UserRepository;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.InternalMemberResponse;
import com.fandom.user_service.member.presentation.dto.response.SignUpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

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

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("일반회원 가입에 성공하면 USER/ACTIVE로 저장되고 응답이 반환된다")
    void signUp_success() {
        // given
        SignUpRequest request = new SignUpRequest("test@example.com", "password123");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignUpResponse response = memberService.signUp(request);

        // then
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo(Role.USER.name());
        assertThat(response.status()).isEqualTo(Status.ACTIVE.name());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 가입하면 DUPLICATE_EMAIL 예외가 발생한다")
    void signUp_duplicateEmail() {
        // given
        SignUpRequest request = new SignUpRequest("dup@example.com", "password123");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        // 중복이면 저장/해싱까지 가지 않는다
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("크리에이터 가입에 성공하면 CREATOR로 저장되고 creators도 함께 저장된다")
    void signUpCreator_success() {
        // given
        CreatorSignUpRequest request =
                new CreatorSignUpRequest("creator@example.com", "password123", "소속사");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignUpResponse response = memberService.signUpCreator(request);

        // then
        assertThat(response.email()).isEqualTo("creator@example.com");
        assertThat(response.role()).isEqualTo(Role.CREATOR.name());
        assertThat(response.status()).isEqualTo(Status.ACTIVE.name());
        verify(userRepository).save(any(User.class));
        verify(creatorRepository).save(any());
    }

    @Test
    @DisplayName("내부 조회 시 존재하는 이메일이면 비밀번호 해시를 포함한 정보를 반환한다")
    void findByEmailForInternal_success() {
        // given
        User user = User.createUser("test@example.com", "encoded-password");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        // when
        InternalMemberResponse response = memberService.findByEmailForInternal("test@example.com");

        // then
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.password()).isEqualTo("encoded-password");
        assertThat(response.role()).isEqualTo(Role.USER.name());
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
