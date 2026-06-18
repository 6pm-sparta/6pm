package com.fandom.notification_service.application.port;

import java.util.UUID;

public interface PushDispatchPort {

    void dispatch(UUID notificationId, UUID userId);

    void publishRetry(UUID notificationId, String deviceToken);
}
