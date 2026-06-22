package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 일반회원 계정 정보 수정 결과 응답.
 */
public record MemberUpdateResponse(
        UUID userId,
        String email,
        LocalDateTime updatedAt
) {
    public static MemberUpdateResponse from(User user) {
        return new MemberUpdateResponse(
                user.getId(),
                user.getEmail(),
                user.getUpdatedAt()
        );
    }
}
