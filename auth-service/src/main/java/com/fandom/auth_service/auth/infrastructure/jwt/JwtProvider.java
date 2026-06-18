package com.fandom.auth_service.auth.infrastructure.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /**
     * Access Token 발급.
     * @param userId 사용자 식별자 (subject)
     * @param role   권한 (MEMBER/CREATOR/MASTER)
     * @param status 계정 상태 (발급 시점 스냅샷)
     */
    public String createAccessToken(UUID userId, String role, String status) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("status", status)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
