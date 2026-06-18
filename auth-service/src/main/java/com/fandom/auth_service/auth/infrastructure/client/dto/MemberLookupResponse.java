package com.fandom.auth_service.auth.infrastructure.client.dto;

import java.util.UUID;

/**
 * user-service 내부 회원 조회(GET /internal/v1/members/{email}) 응답 본문.
 * user-service의 InternalMemberResponse와 필드를 일치시킨다.
 * 로그인 검증을 위해 비밀번호 해시를 포함한다.
 */
public record MemberLookupResponse(
        UUID userId,
        String email,
        String password,
        String role,
        String status
) {
}
