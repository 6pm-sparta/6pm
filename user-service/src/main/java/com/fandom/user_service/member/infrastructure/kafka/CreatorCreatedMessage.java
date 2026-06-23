package com.fandom.user_service.member.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CreatorCreatedMessage(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("nickname") String nickname
) {
}
