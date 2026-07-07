package com.fandom.auth_service.auth.presentation.dto.response;

/**
 * 로그인 응답. Access Token을 반환한다.
 * tokenType은 "Bearer"로 고정.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
