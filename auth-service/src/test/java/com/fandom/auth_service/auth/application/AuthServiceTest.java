package com.fandom.auth_service.auth.application;

import com.fandom.auth_service.auth.domain.exception.AuthErrorCode;
import com.fandom.auth_service.auth.domain.repository.TokenRepository;
import com.fandom.auth_service.auth.infrastructure.client.MemberLookupClient;
import com.fandom.auth_service.auth.infrastructure.client.dto.MemberLookupResponse;
import com.fandom.auth_service.auth.infrastructure.jwt.JwtProvider;
import com.fandom.auth_service.auth.presentation.dto.request.LoginRequest;
import com.fandom.auth_service.auth.presentation.dto.response.LoginResponse;
import com.fandom.auth_service.auth.presentation.dto.response.ReissueResponse;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import feign.FeignException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String REFRESH_TOKEN_ID = "refresh-token-id";
    private static final String ACCESS_TOKEN_ID = "access-token-id";

    private MemberLookupResponse memberOf(String status) {
        return new MemberLookupResponse(USER_ID, EMAIL, ENCODED_PASSWORD, "MEMBER", status);
    }

    private Claims refreshClaims() {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(USER_ID.toString());
        lenient().when(claims.getId()).thenReturn(REFRESH_TOKEN_ID);
        lenient().when(claims.get("role", String.class)).thenReturn("MEMBER");
        lenient().when(claims.get("status", String.class)).thenReturn("ACTIVE");
        return claims;
    }

    private Claims accessClaims() {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(USER_ID.toString());
        lenient().when(claims.getId()).thenReturn(ACCESS_TOKEN_ID);
        return claims;
    }

    @Test
    @DisplayName("올바른 자격증명으로 로그인하면 Access Token이 발급된다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
        Claims refreshClaims = refreshClaims();
        given(memberLookupClient.getMemberByEmail(EMAIL))
                .willReturn(ApiResponse.success(memberOf("ACTIVE")));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtProvider.createAccessToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("access-token");
        given(jwtProvider.createRefreshToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("refresh-token");
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(jwtProvider.getAccessTokenExpiration()).willReturn(1800000L);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(1209600000L);

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800000L);
        // 토큰 Claim 구성에 userId/role/status가 전달되는지 검증
        verify(jwtProvider).createAccessToken(USER_ID, "MEMBER", "ACTIVE");
        verify(tokenRepository).saveRefreshToken(USER_ID, REFRESH_TOKEN_ID, Duration.ofMillis(1209600000L));
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
    @DisplayName("유효한 Refresh Token이면 Access Token을 재발급한다")
    void reissue_success() {
        Claims refreshClaims = refreshClaims();
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(tokenRepository.existsRefreshToken(USER_ID, REFRESH_TOKEN_ID)).willReturn(true);
        given(jwtProvider.createAccessToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("new-access-token");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(1800000L);

        ReissueResponse response = authService.reissue("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800000L);
    }

    @Test
    @DisplayName("Redis에 없는 Refresh Token이면 재발급에 실패한다")
    void reissue_missingRefreshToken() {
        Claims refreshClaims = refreshClaims();
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(tokenRepository.existsRefreshToken(USER_ID, REFRESH_TOKEN_ID)).willReturn(false);

        assertThatThrownBy(() -> authService.reissue("refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Refresh Token rotation 미적용 상태에서는 동일 Refresh Token 동시 재발급을 허용한다")
    void reissue_sameRefreshTokenConcurrently() throws Exception {
        Claims refreshClaims = refreshClaims();
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(tokenRepository.existsRefreshToken(USER_ID, REFRESH_TOKEN_ID)).willReturn(true);
        given(jwtProvider.createAccessToken(USER_ID, "MEMBER", "ACTIVE"))
                .willReturn("new-access-token-1", "new-access-token-2");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(1800000L);

        Callable<ReissueResponse> task = () -> authService.reissue("refresh-token");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReissueResponse>> futures = executor.invokeAll(List.of(task, task));

            assertThat(futures)
                    .extracting(future -> future.get().accessToken())
                    .containsExactlyInAnyOrder("new-access-token-1", "new-access-token-2");
            verify(tokenRepository, times(2)).existsRefreshToken(USER_ID, REFRESH_TOKEN_ID);
            verify(jwtProvider, times(2)).createAccessToken(USER_ID, "MEMBER", "ACTIVE");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("로그인 중 Refresh Token 저장에 실패하면 예외를 전파한다")
    void login_refreshTokenSaveFailure() {
        LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
        Claims refreshClaims = refreshClaims();
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("redis unavailable");
        given(memberLookupClient.getMemberByEmail(EMAIL))
                .willReturn(ApiResponse.success(memberOf("ACTIVE")));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtProvider.createAccessToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("access-token");
        given(jwtProvider.createRefreshToken(USER_ID, "MEMBER", "ACTIVE")).willReturn("refresh-token");
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
        doThrow(redisException)
                .when(tokenRepository)
                .saveRefreshToken(USER_ID, REFRESH_TOKEN_ID, Duration.ofMillis(1209600000L));

        assertThatThrownBy(() -> authService.login(request))
                .isSameAs(redisException);
    }

    @Test
    @DisplayName("재발급 중 Refresh Token 조회에 실패하면 예외를 전파한다")
    void reissue_refreshTokenLookupFailure() {
        Claims refreshClaims = refreshClaims();
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("redis unavailable");
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(tokenRepository.existsRefreshToken(USER_ID, REFRESH_TOKEN_ID)).willThrow(redisException);

        assertThatThrownBy(() -> authService.reissue("refresh-token"))
                .isSameAs(redisException);
    }

    @Test
    @DisplayName("로그아웃하면 Refresh Token을 삭제하고 Access Token을 blacklist에 등록한다")
    void logout_success() {
        Claims accessClaims = accessClaims();
        Claims refreshClaims = refreshClaims();
        Duration accessTtl = Duration.ofMinutes(10);
        given(jwtProvider.parse("access-token")).willReturn(accessClaims);
        given(jwtProvider.isAccessToken(accessClaims)).willReturn(true);
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(jwtProvider.getRemainingTtl(accessClaims)).willReturn(accessTtl);

        authService.logout("Bearer access-token", "refresh-token");

        verify(tokenRepository).deleteRefreshToken(USER_ID, REFRESH_TOKEN_ID);
        verify(tokenRepository).blacklistAccessToken(ACCESS_TOKEN_ID, accessTtl);
    }

    @Test
    @DisplayName("로그아웃 중 Refresh Token 삭제에 실패하면 예외를 전파한다")
    void logout_refreshTokenDeleteFailure() {
        Claims accessClaims = accessClaims();
        Claims refreshClaims = refreshClaims();
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("redis unavailable");
        given(jwtProvider.parse("access-token")).willReturn(accessClaims);
        given(jwtProvider.isAccessToken(accessClaims)).willReturn(true);
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        doThrow(redisException).when(tokenRepository).deleteRefreshToken(USER_ID, REFRESH_TOKEN_ID);

        assertThatThrownBy(() -> authService.logout("Bearer access-token", "refresh-token"))
                .isSameAs(redisException);
    }

    @Test
    @DisplayName("로그아웃 중 Access Token blacklist 등록에 실패하면 예외를 전파한다")
    void logout_accessTokenBlacklistFailure() {
        Claims accessClaims = accessClaims();
        Claims refreshClaims = refreshClaims();
        Duration accessTtl = Duration.ofMinutes(10);
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("redis unavailable");
        given(jwtProvider.parse("access-token")).willReturn(accessClaims);
        given(jwtProvider.isAccessToken(accessClaims)).willReturn(true);
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);
        given(jwtProvider.getRemainingTtl(accessClaims)).willReturn(accessTtl);
        doThrow(redisException).when(tokenRepository).blacklistAccessToken(ACCESS_TOKEN_ID, accessTtl);

        assertThatThrownBy(() -> authService.logout("Bearer access-token", "refresh-token"))
                .isSameAs(redisException);
    }

    @Test
    @DisplayName("access\uc640 refresh\uc758 \uc18c\uc720\uc790(userId)\uac00 \ub2e4\ub974\uba74 \ub85c\uadf8\uc544\uc6c3\uc744 \uac70\ubd80\ud55c\ub2e4")
    void logout_userIdMismatch() {
        Claims accessClaims = accessClaims();
        Claims refreshClaims = mock(Claims.class);
        // refresh\uc758 subject\ub97c \ub2e4\ub978 userId\ub85c \ub454\ub2e4 (access\uc640 \ubd88\uc77c\uce58)
        lenient().when(refreshClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
        lenient().when(refreshClaims.getId()).thenReturn(REFRESH_TOKEN_ID);
        given(jwtProvider.parse("access-token")).willReturn(accessClaims);
        given(jwtProvider.isAccessToken(accessClaims)).willReturn(true);
        given(jwtProvider.parse("refresh-token")).willReturn(refreshClaims);
        given(jwtProvider.isRefreshToken(refreshClaims)).willReturn(true);

        assertThatThrownBy(() -> authService.logout("Bearer access-token", "refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
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
