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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    // 보관함 커서 페이지네이션
    List<Notification> findByUserIdOrderByIdDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(UUID userId, UUID cursor, Pageable pageable);

    // 멱등성 일괄 체크
    @Query("SELECT n.userId FROM Notification n " +
           "WHERE n.referenceId = :referenceId AND n.type = :type AND n.userId IN :userIds")
    List<UUID> findUserIdsByReferenceIdAndTypeAndUserIdIn(
            @Param("referenceId") UUID referenceId,
            @Param("type") NotificationType type,
            @Param("userIds") Collection<UUID> userIds);

    List<Notification> findBySendStatusAndCreatedAtBefore(
            NotificationSendStatus status, LocalDateTime cutoff, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.deletedAt = :now, n.deletedBy = :userId " +
           "WHERE n.userId = :userId AND n.deletedAt IS NULL")
    void softDeleteAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
