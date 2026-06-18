package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;

import java.util.UUID;

/**
 * 가입 결과 응답. (외부 노출용 — 비밀번호 포함 안 함)
 */
public record SignUpResponse(
        UUID userId,
        String email,
        String role,
        String status
) {
    public static SignUpResponse from(User user) {
        return new SignUpResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
