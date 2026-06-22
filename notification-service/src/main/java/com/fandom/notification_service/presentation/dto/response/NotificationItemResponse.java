package com.fandom.notification_service.presentation.dto.response;

import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationType;

import java.util.UUID;

public record NotificationItemResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        boolean isRead
) {
    public static NotificationItemResponse from(Notification n) {
        return new NotificationItemResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.isRead());
    }
}
