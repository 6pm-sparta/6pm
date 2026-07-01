package com.fandom.gateway_service.filter;

import com.fandom.common.auth.HmacUtils;
import com.fandom.gateway_service.jwt.JwtValidator;
import com.fandom.gateway_service.security.GatewaySecurityRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthenticationFilter 단위 테스트.
 * 협력자(JwtValidator, HmacUtils, Redis)는 목킹하고, 인증 분기 동작만 검증한다.
 */
@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterTest {

    private JwtValidator jwtValidator;
    private HmacUtils hmacUtils;
    private ReactiveStringRedisTemplate redisTemplate;
    private GatewayFilterChain chain;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtValidator = mock(JwtValidator.class);
        hmacUtils = mock(HmacUtils.class);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());
        filter = new JwtAuthenticationFilter(jwtValidator, hmacUtils, new ObjectMapper(), redisTemplate,
                new GatewaySecurityRules());
    }

    private static final UUID USER_ID = UUID.randomUUID();

    private Claims claimsOf(String jti) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(USER_ID.toString());
        lenient().when(claims.get("role", String.class)).thenReturn("MEMBER");
        lenient().when(claims.getId()).thenReturn(jti);
        return claims;
    }

    @Test
    @DisplayName("로그인은 인증 예외 경로라 토큰 없이 통과한다")
    void login_permitAll() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("재발급(reissue)도 인증 예외 경로라 토큰 없이 통과한다")
    void reissue_permitAll() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/reissue").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("토큰이 없으면 401로 거부한다")
    void noToken_unauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("형식이 잘못된 토큰이면 401로 거부한다")
    void invalidToken_unauthorized() {
        given(jwtValidator.parse(anyString())).willThrow(new JwtException("invalid"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer bad-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("유효한 토큰이고 blacklist에 없으면 통과한다")
    void validToken_notBlacklisted_pass() {
        Claims claims = claimsOf("jti-1");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        given(hmacUtils.sign(any())).willReturn("sig");
        given(redisTemplate.hasKey("blacklist:access:jti-1")).willReturn(Mono.just(false));
        given(redisTemplate.hasKey("blacklist:user:" + USER_ID)).willReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer good-token").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("유효한 토큰이어도 blacklist에 있으면(로그아웃된 토큰) 401로 차단한다")
    void validToken_blacklisted_unauthorized() {
        Claims claims = claimsOf("jti-2");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        given(redisTemplate.hasKey("blacklist:access:jti-2")).willReturn(Mono.just(true));
        given(redisTemplate.hasKey("blacklist:user:" + USER_ID)).willReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer logged-out-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("사용자 단위 blacklist에 있으면(탈퇴 회원 토큰) 401로 차단한다")
    void validToken_userBlacklisted_unauthorized() {
        Claims claims = claimsOf("jti-3");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        given(redisTemplate.hasKey("blacklist:access:jti-3")).willReturn(Mono.just(false));
        given(redisTemplate.hasKey("blacklist:user:" + USER_ID)).willReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer withdrawn-user-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ===== 비정상 claim 차단 (claim 무결성 검증) =====

    /** subject/role 을 직접 지정한 claims (비정상 케이스용). */
    private Claims claimsWith(String subject, String role) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(subject);
        lenient().when(claims.get("role", String.class)).thenReturn(role);
        lenient().when(claims.getId()).thenReturn("jti-x");
        return claims;
    }

    @Test
    @DisplayName("subject(userId)가 없으면 401로 차단한다")
    void subjectMissing_unauthorized() {
        Claims claims = claimsWith(null, "MEMBER");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("subject가 UUID 형식이 아니면 401로 차단한다")
    void subjectNotUuid_unauthorized() {
        Claims claims = claimsWith("not-a-uuid", "MEMBER");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("role claim이 없으면 401로 차단한다")
    void roleMissing_unauthorized() {
        Claims claims = claimsWith(USER_ID.toString(), null);
        given(jwtValidator.parse(anyString())).willReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    @Test
    @DisplayName("Access Token blacklist 조회에 실패하면 503으로 차단한다")
    void accessBlacklistLookupFailure_serviceUnavailable() {
        Claims claims = claimsOf("jti-4");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        given(redisTemplate.hasKey("blacklist:access:jti-4"))
                .willReturn(Mono.error(new RuntimeException("redis unavailable")));
        given(redisTemplate.hasKey("blacklist:user:" + USER_ID)).willReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer redis-error-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("role claim이 공백이면 401로 차단한다")
    void roleBlank_unauthorized() {
        Claims claims = claimsWith(USER_ID.toString(), "  ");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("비정상 claim은 blacklist 조회(Redis) 이전에 차단되어 Redis를 조회하지 않는다")
    void invalidClaim_blockedBeforeRedis() {
        Claims claims = claimsWith(null, "MEMBER");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, chain).block();

        // claim 무결성 검증이 blacklist 조회보다 먼저이므로 Redis hasKey 가 호출되지 않아야 한다
        verify(redisTemplate, never()).hasKey(anyString());
    }
    @DisplayName("사용자 단위 blacklist 조회에 실패하면 503으로 차단한다")
    void userBlacklistLookupFailure_serviceUnavailable() {
        Claims claims = claimsOf("jti-5");
        given(jwtValidator.parse(anyString())).willReturn(claims);
        given(redisTemplate.hasKey("blacklist:access:jti-5")).willReturn(Mono.just(false));
        given(redisTemplate.hasKey("blacklist:user:" + USER_ID))
                .willReturn(Mono.error(new RuntimeException("redis unavailable")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer redis-error-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any());
    }
}
