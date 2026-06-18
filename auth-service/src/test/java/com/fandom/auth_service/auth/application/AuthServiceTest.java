package com.fandom.auth_service.auth.application;

import com.fandom.auth_service.auth.domain.exception.AuthErrorCode;
import com.fandom.auth_service.auth.infrastructure.client.MemberLookupClient;
import com.fandom.auth_service.auth.infrastructure.client.dto.MemberLookupResponse;
import com.fandom.auth_service.auth.infrastructure.jwt.JwtProvider;
import com.fandom.auth_service.auth.presentation.dto.request.LoginRequest;
import com.fandom.auth_service.auth.presentation.dto.response.LoginResponse;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock
    private MemberLookupClient memberLookupClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password";

    private MemberLookupResponse memberOf(String status) {
        return new MemberLookupResponse(USER_ID, EMAIL, ENCODED_PASSWORD, "MEMBER", status);
    }

    @Test
    @DisplayName("올바른 자격증명으로 로그인하면 Access Token이 발급된다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
        given(memberLookupClient.getMemberByEmail(EMAIL))
                .willReturn(ApiResponse.success(memberOf("ACTIVE")));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtProvider.createAccessToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("access-token");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(1800000L);

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800000L);
        // 토큰 Claim 구성에 userId/role/status가 전달되는지 검증
        verify(jwtProvider).createAccessToken(USER_ID, "MEMBER", "ACTIVE");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 LOGIN_FAILED 예외가 발생한다")
    void login_wrongPassword() {
        // given
        LoginRequest request = new LoginRequest(EMAIL, "wrongpass");
        given(memberLookupClient.getMemberByEmail(EMAIL))
                .willReturn(ApiResponse.success(memberOf("ACTIVE")));
        given(passwordEncoder.matches("wrongpass", ENCODED_PASSWORD)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 회원이면(Feign 404) 계정 노출 없이 LOGIN_FAILED로 변환된다")
    void login_memberNotFound() {
        // given
        LoginRequest request = new LoginRequest("none@example.com", RAW_PASSWORD);
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        given(memberLookupClient.getMemberByEmail("none@example.com")).willThrow(notFound);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("계정이 ACTIVE가 아니면 INACTIVE_MEMBER 예외가 발생한다")
    void login_inactiveMember() {
        // given
        LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
        given(memberLookupClient.getMemberByEmail(EMAIL))
                .willReturn(ApiResponse.success(memberOf("SUSPENDED")));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INACTIVE_MEMBER);
    }
}
