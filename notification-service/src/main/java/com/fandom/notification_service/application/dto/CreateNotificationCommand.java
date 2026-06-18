package com.fandom.notification_service.application.dto;

import com.fandom.notification_service.domain.entity.NotificationType;

import java.util.List;
import java.util.UUID;

public record CreateNotificationCommand(
        UUID referenceId,
        NotificationType type,
        String title,
        String content,
        List<UUID> targetUserIds
) {
}
