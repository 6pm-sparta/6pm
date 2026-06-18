package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 일반회원 가입 결과 응답. (외부 노출용 - 비밀번호 포함 안 함)
 */
public record MemberSignUpResponse(
        UUID userId,
        String email,
        String nickname,
        String role,
        String status,
        LocalDateTime createdAt
) {
    public static MemberSignUpResponse from(User user, Profile profile) {
        return new MemberSignUpResponse(
                user.getId(),
                user.getEmail(),
                profile.getNickname(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
