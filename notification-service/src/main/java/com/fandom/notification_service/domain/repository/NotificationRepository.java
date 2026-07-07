package com.fandom.notification_service.domain.repository;

import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> saveAll(List<Notification> notifications);

    Optional<Notification> findById(UUID id);

    // 멱등성 일괄 체크
    List<UUID> findExistingUserIds(UUID referenceId, NotificationType type, Collection<UUID> userIds);

    // 보관함 커서 조회
    List<Notification> findInbox(UUID userId, UUID cursor, int limit);

    void softDeleteAllByUserId(UUID userId);

    // Pending 상태 5분 후 재시도
    List<Notification> findPendingCreatedBefore(LocalDateTime cutoff, int limit);
}
