package com.fandom.auth_service.auth.infrastructure.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Access Token(JWT) 발급기. HS256 대칭키 서명.
 *
 * 발급한 토큰은 Gateway가 동일한 secret으로 검증한다(키 공유 필요).
 * 토큰 payload: subject=userId, claim "role"=권한(MEMBER/CREATOR/MASTER), claim "status"=계정 상태.
 *
 * 참고: status는 발급 시점의 스냅샷이다(토큰 만료 전 상태 변경은 즉시 반영되지 않음).
 * 실시간 상태 판단이 필요하면 토큰 claim 대신 별도 조회를 사용해야 한다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Access Token 발급.
     * @param userId 사용자 식별자 (subject)
     * @param role   권한 (MEMBER/CREATOR/MASTER)
     * @param status 계정 상태 (발급 시점 스냅샷)
     */
    public String createAccessToken(UUID userId, String role, String status) {
        return createToken(userId, role, status, ACCESS_TOKEN_TYPE, accessTokenExpiration);
    }

    public String createRefreshToken(UUID userId, String role, String status) {
        return createToken(userId, role, status, REFRESH_TOKEN_TYPE, refreshTokenExpiration);
    }

    private String createToken(UUID userId, String role, String status, String tokenType, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("role", role)
                .claim("status", status)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public io.jsonwebtoken.Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(io.jsonwebtoken.Claims claims) {
        return REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isAccessToken(io.jsonwebtoken.Claims claims) {
        return ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public Duration getRemainingTtl(io.jsonwebtoken.Claims claims) {
        Instant expiration = claims.getExpiration().toInstant();
        return Duration.between(Instant.now(), expiration);
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
