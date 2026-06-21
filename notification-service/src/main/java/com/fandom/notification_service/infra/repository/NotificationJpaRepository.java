package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationSendStatus;
import com.fandom.notification_service.domain.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    // 보관함 커서 페이지네이션
    List<Notification> findByUserIdOrderByIdDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(UUID userId, UUID cursor, Pageable pageable);

    boolean existsByUserIdAndTypeAndReferenceId(UUID userId, NotificationType type, UUID referenceId);

    List<Notification> findBySendStatusAndCreatedAtBefore(
            NotificationSendStatus status, LocalDateTime cutoff, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.deletedAt = :now, n.deletedBy = :userId " +
           "WHERE n.userId = :userId AND n.deletedAt IS NULL")
    void softDeleteAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
