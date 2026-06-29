package com.fandom.chat_service.presentation.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record NotificationSendMessage(
        @JsonProperty("reference_id") UUID referenceId,
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("target_user_ids") List<UUID> targetUserIds
) {
}
