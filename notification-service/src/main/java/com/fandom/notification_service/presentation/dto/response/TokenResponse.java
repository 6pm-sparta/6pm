package com.fandom.notification_service.presentation.dto.response;

import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.domain.entity.UserNotificationToken;

import java.util.UUID;

public record TokenResponse(
        UUID id,
        DeviceType deviceType,
        boolean isNotified
) {
    public static TokenResponse from(UserNotificationToken token) {
        return new TokenResponse(token.getId(), token.getDeviceType(), token.isNotified());
    }
}
