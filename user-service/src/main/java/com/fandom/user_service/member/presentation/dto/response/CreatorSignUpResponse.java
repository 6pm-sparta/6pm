package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.User;
import com.fandom.user_service.profile.domain.entity.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 크리에이터 가입 결과 응답. (외부 노출용 - 비밀번호 포함 안 함)
 */
public record CreatorSignUpResponse(
        UUID userId,
        String email,
        String nickname,
        String role,
        String agencyName,
        String status,
        LocalDateTime createdAt
) {
    public static CreatorSignUpResponse from(User user, Profile profile, Creator creator) {
        return new CreatorSignUpResponse(
                user.getId(),
                user.getEmail(),
                profile.getNickname(),
                user.getRole().name(),
                creator.getAgencyName(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
