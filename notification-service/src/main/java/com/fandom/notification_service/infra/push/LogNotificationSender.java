package com.fandom.notification_service.infra.push;

import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.domain.entity.DeviceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(String deviceToken, DeviceType deviceType, String title, String body) {
        log.info("[PUSH-STUB] {} 발송: token={}, title={}", deviceType, deviceToken, title);
        // 실제 FCM/APNs 연동은 추후.
    }
}
