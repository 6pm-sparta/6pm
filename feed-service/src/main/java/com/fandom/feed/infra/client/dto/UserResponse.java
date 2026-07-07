package com.fandom.feed.infra.client.dto;

import java.util.Map;
import java.util.UUID;

public record UserResponse(UUID userId, String nickname) {
    public static UserResponse of(UUID userId, Map<UUID, UserResponse> userMap) {
        if (userMap.containsKey(userId)) return userMap.get(userId);
        return new UserResponse(userId, "탈퇴한 사용자");
    }
}