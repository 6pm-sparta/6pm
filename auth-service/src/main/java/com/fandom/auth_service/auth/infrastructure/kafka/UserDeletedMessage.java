package com.fandom.auth_service.auth.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserDeletedMessage(
        @JsonProperty("user_id") UUID userId
) {
}
