package com.fandom.notification_service.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record SettingUpdateRequest(
        @NotNull Boolean isNotified
) {
}
