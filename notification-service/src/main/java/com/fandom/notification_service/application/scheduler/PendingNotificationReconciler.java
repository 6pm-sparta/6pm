package com.fandom.notification_service.application.scheduler;

import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingNotificationReconciler {

    // 100개씩 5분마다 5분이상 지난 Pending 재발행
    private static final int BATCH_SIZE = 100;
    private static final long CUTOFF_MINUTES = 5;
    private static final long INTERVAL_MS = 5 * 60 * 1000L;

    private final NotificationRepository notificationRepository;
    private final PushDispatchPort pushDispatchPort;

    @Scheduled(fixedDelay = INTERVAL_MS)
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(CUTOFF_MINUTES);
        List<Notification> stuck = notificationRepository.findPendingCreatedBefore(cutoff, BATCH_SIZE);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("PENDING 재조정: {}건 재발행", stuck.size());
        for (Notification n : stuck) {
            pushDispatchPort.dispatch(n.getId(), n.getUserId());
        }
    }
}
