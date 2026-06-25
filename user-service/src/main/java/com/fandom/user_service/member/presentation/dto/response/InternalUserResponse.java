package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.profile.domain.entity.Profile;

import java.util.UUID;

/**
 * 내부 사용자 조회 응답. ( /internal/v1/users/{userId}, /internal/v1/users 전용 )
 * feed-service 등 타 서비스가 게시글/댓글 작성자 정보를 조합할 때 사용한다.
 * 닉네임 등 공개 프로필 정보만 포함하며, 민감 정보(비밀번호 등)는 포함하지 않는다.
 * ⚠️ 내부 서비스 간 통신 전용. 외부(Gateway 경유) 응답에는 사용하지 않는다.
 */
public record InternalUserResponse(
        UUID userId,
        String nickname
) {
    public static InternalUserResponse from(Profile profile) {
        return new InternalUserResponse(
                profile.getUser().getId(),
                profile.getNickname()
        );
    }
}
