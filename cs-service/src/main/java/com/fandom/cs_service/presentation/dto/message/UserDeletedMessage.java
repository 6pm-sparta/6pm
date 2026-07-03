package com.fandom.cs_service.presentation.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserDeletedMessage(
        @JsonProperty("user_id") UUID userId
) {
}
