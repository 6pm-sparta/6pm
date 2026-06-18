package com.fandom.notification_service.presentation.dto;

import com.fandom.notification_service.domain.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record NotificationSendMessage(
        @JsonProperty("reference_id") UUID referenceId,
        @JsonProperty("type") NotificationType type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("target_user_ids") List<UUID> targetUserIds
) {
}
