package com.fandom.gateway_service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * auth-service가 발급한 Access Token을 검증한다.
 * auth의 JwtProvider와 동일한 secret/알고리즘(HS512, 키 길이로 자동 결정)을 사용해야 검증이 성립한다.
 */
@Component
public class JwtValidator {

    private final SecretKey secretKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰을 검증하고 Claims를 반환한다.
     * 서명 불일치 / 만료 / 형식 오류 시 JwtException 계열 예외가 발생한다.
     * payload: subject=userId, claim "role", claim "status"
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
