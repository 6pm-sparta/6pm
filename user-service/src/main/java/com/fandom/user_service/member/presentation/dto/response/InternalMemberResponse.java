package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.User;

import java.util.UUID;

/**
 * 내부 회원 조회 응답. ( /internal/v1/members/{email} 전용 )
 * 로그인 검증을 위해 비밀번호 해시를 포함한다.
 * ⚠️ 내부 서비스 간 통신 전용. 외부(Gateway 경유) 응답에는 절대 사용 금지.
 */
public record InternalMemberResponse(
        UUID userId,
        String email,
        String password,
        String role,
        String status
) {
    public static InternalMemberResponse from(User user) {
        return new InternalMemberResponse(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
