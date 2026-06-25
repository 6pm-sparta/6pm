package com.fandom.chat_service.application.port;

import java.util.List;
import java.util.UUID;

public interface ChatNotificationPort {

    void notifyNewMessage(UUID referenceId, String title, String content, List<UUID> targetUserIds);
}
