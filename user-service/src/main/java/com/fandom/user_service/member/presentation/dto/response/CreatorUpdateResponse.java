package com.fandom.user_service.member.presentation.dto.response;

import com.fandom.user_service.member.domain.entity.Creator;
import com.fandom.user_service.member.domain.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 크리에이터 계정 정보 수정 결과 응답.
 */
public record CreatorUpdateResponse(
        UUID userId,
        String email,
        String agencyName,
        LocalDateTime updatedAt
) {
    public static CreatorUpdateResponse from(User user, Creator creator) {
        return new CreatorUpdateResponse(
                user.getId(),
                user.getEmail(),
                creator.getAgencyName(),
                user.getUpdatedAt()
        );
    }
}
