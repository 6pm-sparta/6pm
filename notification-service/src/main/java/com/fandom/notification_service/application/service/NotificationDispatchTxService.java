package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.DeliveryResult;
import com.fandom.notification_service.application.dto.DeliveryTarget;
import com.fandom.notification_service.application.dto.DispatchPlan;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationDelivery;
import com.fandom.notification_service.domain.entity.NotificationSendStatus;
import com.fandom.notification_service.domain.entity.UserNotificationToken;
import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import com.fandom.notification_service.support.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class NotificationDispatchTxService {

    @Value("${notification.dispatch.max-attempt:3}")
    private int maxAttempt;

    private final NotificationRepository notificationRepository;
    private final UserNotificationTokenRepository tokenRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final PushDispatchPort pushDispatchPort;

    // 알림/토큰 로드, PENDING delivery 생성, 발송 대상 반환
    @Transactional
    public DispatchPlan prepare(UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) {
            log.warn("발송 대상 없음 id={}", notificationId);
            return DispatchPlan.empty();
        }
        if (n.getSendStatus() == NotificationSendStatus.SUCCESS) {
            return DispatchPlan.empty();
        }

        List<UserNotificationToken> tokens = tokenRepository.findAllByUserIdAndNotifiedTrue(n.getUserId());
        if (tokens.isEmpty()) {
            n.markAsSuccess(); // 보낼 기기 없음
            notificationRepository.save(n);
            return DispatchPlan.empty();
        }

        Map<String, NotificationDelivery> deliveryMap = deliveryRepository.findAllByNotificationId(notificationId).stream()
                .collect(Collectors.toMap(NotificationDelivery::getDeviceToken, d -> d));

        List<DeliveryTarget> targets = new ArrayList<>();
        for (UserNotificationToken t : tokens) {
            NotificationDelivery delivery = deliveryMap.get(t.getDeviceToken());
            if (delivery == null) { // 최초 발송분만 insert
                deliveryRepository.save(NotificationDelivery.builder()
                        .notificationId(notificationId)
                        .deviceToken(t.getDeviceToken())
                        .deviceType(t.getDeviceType())
                        .build());
            } else if (delivery.getStatus() == NotificationSendStatus.SUCCESS) {
                continue; // 이미 성공한 기기 제외
            }
            targets.add(new DeliveryTarget(t.getDeviceToken(), t.getDeviceType()));
        }
        return new DispatchPlan(n.getTitle(), n.getBody(), targets);
    }

    // 외부 발송 결과로 delivery 상태 갱신 + 알림 집계 + 실패분 재시도 발행
    @Transactional
    public void record(UUID notificationId, List<DeliveryResult> results) {
        List<String> retryTokens = new ArrayList<>();
        for (DeliveryResult r : results) {
            NotificationDelivery d = deliveryRepository
                    .findByNotificationIdAndDeviceToken(notificationId, r.deviceToken())
                    .orElse(null);
            if (d == null) {
                continue;
            }
            if (r.success()) {
                d.markSuccess();
            } else {
                d.markFailed();
                if (d.getAttemptCount() < maxAttempt) {
                    retryTokens.add(r.deviceToken());
                } else {
                    log.error("발송 한계 도달, 포기 id={}, token={}", notificationId, LogMask.token(r.deviceToken()));
                }
            }
            deliveryRepository.save(d);
        }
        recomputeAggregate(notificationId);
        publishRetryAfterCommit(notificationId, retryTokens);
    }

    // 재발송 준비
    @Transactional(readOnly = true)
    public DispatchPlan prepareRetry(UUID notificationId, String deviceToken) {
        NotificationDelivery d = deliveryRepository
                .findByNotificationIdAndDeviceToken(notificationId, deviceToken)
                .orElse(null);
        if (d == null) {
            log.warn("재발송 delivery 없음 id={}, token={}", notificationId, LogMask.token(deviceToken));
            return DispatchPlan.empty();
        }
        if (d.getStatus() == NotificationSendStatus.SUCCESS) {
            return DispatchPlan.empty();
        }
        if (d.getAttemptCount() >= maxAttempt) {
            log.error("재발송 한계 도달, 포기 id={}, token={}", notificationId, LogMask.token(deviceToken));
            return DispatchPlan.empty();
        }
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) {
            log.warn("재발송 대상 알림 없음 id={}", notificationId);
            return DispatchPlan.empty();
        }
        return new DispatchPlan(n.getTitle(), n.getBody(), List.of(new DeliveryTarget(deviceToken, d.getDeviceType())));
    }

    // 재발송 기록
    @Transactional
    public void recordRetry(UUID notificationId, String deviceToken, boolean success) {
        NotificationDelivery d = deliveryRepository
                .findByNotificationIdAndDeviceToken(notificationId, deviceToken)
                .orElse(null);
        if (d == null) {
            return;
        }
        if (success) {
            d.markSuccess();
            deliveryRepository.save(d);
            recomputeAggregate(notificationId);
        } else {
            d.markFailed();
            deliveryRepository.save(d);
            if (d.getAttemptCount() < maxAttempt) {
                publishRetryAfterCommit(notificationId, List.of(deviceToken));
            } else {
                log.error("재발송 최종 실패(포기) id={}, token={}", notificationId, LogMask.token(deviceToken));
            }
        }
    }

    // 기기 별 발송 현황 -> 알림 최종 상태 저장(성공/실패)
    private void recomputeAggregate(UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) {
            return;
        }
        List<NotificationDelivery> all = deliveryRepository.findAllByNotificationId(notificationId);
        boolean allSuccess = !all.isEmpty()
                && all.stream().allMatch(d -> d.getStatus() == NotificationSendStatus.SUCCESS);
        if (allSuccess) {
            n.markAsSuccess();
        } else {
            n.markAsFailed();
        }
        notificationRepository.save(n);
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
