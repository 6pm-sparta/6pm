package com.fandom.notification_service.infra.push;

import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.support.LogMask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// fcm.enabled=false 일 때 로그로 대체
@Slf4j
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificationSender implements NotificationSender {

    @Override
    public void send(String deviceToken, DeviceType deviceType, String title, String body) {
        log.info("[PUSH-STUB] {} 발송: token={}, title={}", deviceType, LogMask.token(deviceToken), title);
    }
}
