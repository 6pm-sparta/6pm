package com.fandom.notification_service.application.port;

import com.fandom.notification_service.domain.entity.DeviceType;

public interface NotificationSender {

    void send(String deviceToken, DeviceType deviceType, String title, String body);
}
