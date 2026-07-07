package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationSendStatus;
import com.fandom.notification_service.domain.entity.NotificationType;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public List<Notification> saveAll(List<Notification> notifications) {
        return jpaRepository.saveAll(notifications);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<UUID> findExistingUserIds(UUID referenceId, NotificationType type, Collection<UUID> userIds) {
        return jpaRepository.findUserIdsByReferenceIdAndTypeAndUserIdIn(referenceId, type, userIds);
    }

    @Override
    public List<Notification> findInbox(UUID userId, UUID cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return cursor == null
                ? jpaRepository.findByUserIdOrderByIdDesc(userId, page)
                : jpaRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, page);
    }

    @Override
    public void softDeleteAllByUserId(UUID userId) {
        jpaRepository.softDeleteAllByUserId(userId, LocalDateTime.now());
    }

    @Override
    public List<Notification> findPendingCreatedBefore(LocalDateTime cutoff, int limit) {
        return jpaRepository.findBySendStatusAndCreatedAtBefore(
                NotificationSendStatus.PENDING, cutoff, PageRequest.of(0, limit));
    }
}
