package com.fandom.chat_service.presentation.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// user.deleted
public record UserDeletedMessage(
        @JsonProperty("user_id") UUID userId
) {
}
