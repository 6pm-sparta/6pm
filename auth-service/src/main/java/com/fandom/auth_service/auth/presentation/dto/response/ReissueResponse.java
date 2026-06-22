package com.fandom.auth_service.auth.presentation.dto.response;

public record ReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static ReissueResponse of(String accessToken, long expiresIn) {
        return new ReissueResponse(accessToken, "Bearer", expiresIn);
    }
}
