package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationDelivery;
import com.fandom.notification_service.domain.entity.NotificationSendStatus;
import com.fandom.notification_service.domain.entity.UserNotificationToken;
import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final int MAX_ATTEMPT = 3;

    private final NotificationRepository notificationRepository;
    private final UserNotificationTokenRepository tokenRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationSender notificationSender;
    private final PushDispatchPort pushDispatchPort;

    @Transactional
    public void dispatch(UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) {
            log.warn("발송 대상 없음 id={}", notificationId);
            return;
        }
        if (n.getSendStatus() == NotificationSendStatus.SUCCESS) {
            return; // 성공 처리됨
        }

        List<UserNotificationToken> tokens = tokenRepository.findAllByUserIdAndNotifiedTrue(n.getUserId());
        if (tokens.isEmpty()) {
            n.markAsSuccess(); // 보낼 기기 없음
            notificationRepository.save(n);
            return;
        }

        Map<String, NotificationDelivery> deliveryMap = deliveryRepository.findAllByNotificationId(notificationId).stream()
                .collect(Collectors.toMap(NotificationDelivery::getDeviceToken, d -> d));

        boolean allOk = true;
        List<String> retryTokens = new ArrayList<>(); // 커밋 후 재발행할 실패 기기
        for (UserNotificationToken t : tokens) {
            NotificationDelivery delivery = deliveryMap.get(t.getDeviceToken());
            if (delivery == null) { // 최초 발송분만 insert
                delivery = deliveryRepository.save(NotificationDelivery.builder()
                        .notificationId(notificationId)
                        .deviceToken(t.getDeviceToken())
                        .deviceType(t.getDeviceType())
                        .build());
            }

            if (delivery.getStatus() == NotificationSendStatus.SUCCESS) {
                continue; // 성공 처리 된 기기
            }

            try {
                notificationSender.send(t.getDeviceToken(), t.getDeviceType(), n.getTitle(), n.getBody());
                delivery.markSuccess();
                deliveryRepository.save(delivery);
            } catch (Exception e) {
                delivery.markFailed();
                deliveryRepository.save(delivery);
                allOk = false;
                log.error("기기 발송 실패 token={}, id={}", t.getDeviceToken(), notificationId, e);
                if (delivery.getAttemptCount() < MAX_ATTEMPT) {
                    retryTokens.add(t.getDeviceToken());
                }
            }
        }

        if (allOk) {
            n.markAsSuccess();
        } else {
            n.markAsFailed();
        }
        notificationRepository.save(n);

        publishRetryAfterCommit(notificationId, retryTokens);
    }



    // 트랜잭션 커밋 이후 push.failed 발행
    private void publishRetryAfterCommit(UUID notificationId, List<String> deviceTokens) {
        if (deviceTokens.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deviceTokens.forEach(token -> pushDispatchPort.publishRetry(notificationId, token));
            }
        });
    }
}
