package com.fandom.notification_service.application.scheduler;

import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingNotificationReconciler {

    // 기본 100개씩 5분마다 5분이상 지난 Pending 재발행
    @Value("${notification.reconciler.batch-size:100}")
    private int batchSize;
    @Value("${notification.reconciler.cutoff-minutes:5}")
    private long cutoffMinutes;

    private final NotificationRepository notificationRepository;
    private final PushDispatchPort pushDispatchPort;

    @Scheduled(fixedDelayString = "${notification.reconciler.interval-ms:300000}")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(cutoffMinutes);
        List<Notification> stuck = notificationRepository.findPendingCreatedBefore(cutoff, batchSize);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("PENDING 재조정: {}건 재발행", stuck.size());
        for (Notification n : stuck) {
            pushDispatchPort.dispatch(n.getId(), n.getUserId());
        }
    }
}
