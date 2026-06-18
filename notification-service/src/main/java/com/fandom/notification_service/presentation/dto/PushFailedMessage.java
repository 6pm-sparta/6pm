package com.fandom.notification_service.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PushFailedMessage(
        @JsonProperty("notification_id") UUID notificationId,
        @JsonProperty("device_token") String deviceToken
) {
}
