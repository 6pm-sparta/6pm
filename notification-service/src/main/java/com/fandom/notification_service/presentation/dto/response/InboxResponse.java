package com.fandom.notification_service.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record InboxResponse(
        List<NotificationItemResponse> notifications,
        UUID nextCursor,
        boolean hasNext
) {
}
