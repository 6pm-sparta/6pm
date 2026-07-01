package com.fandom.notification_service.infra.push;

import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.support.LogMask;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// FCM 발송
@Slf4j
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FcmSender implements NotificationSender {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public void send(String deviceToken, DeviceType deviceType, String title, String body) {
        Message message = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build();
        try {
            String messageId = firebaseMessaging.send(message);
            log.debug("FCM 발송 성공 messageId={}, token={}", messageId, LogMask.token(deviceToken));
        } catch (FirebaseMessagingException e) {
            throw new IllegalStateException("FCM 발송 실패: " + e.getMessagingErrorCode(), e);
        }
    }
}
