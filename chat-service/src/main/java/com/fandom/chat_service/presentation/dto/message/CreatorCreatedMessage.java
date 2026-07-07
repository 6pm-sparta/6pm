package com.fandom.chat_service.presentation.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// user.creator-created
public record CreatorCreatedMessage(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("nickname") String nickname
) {
}
