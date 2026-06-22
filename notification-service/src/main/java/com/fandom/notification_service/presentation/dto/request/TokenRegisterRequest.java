package com.fandom.notification_service.presentation.dto.request;

import com.fandom.notification_service.domain.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenRegisterRequest(
        @NotBlank String deviceToken,
        @NotNull DeviceType deviceType
) {
}
