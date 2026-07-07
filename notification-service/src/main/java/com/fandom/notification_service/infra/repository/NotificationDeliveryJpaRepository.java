package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findByNotificationIdAndDeviceToken(UUID notificationId, String deviceToken);

    List<NotificationDelivery> findAllByNotificationId(UUID notificationId);

    // 유저의 알림의 전달 레코드 하드 삭제
    @Transactional
    @Modifying
    @Query("DELETE FROM NotificationDelivery d " +
           "WHERE d.notificationId IN (SELECT n.id FROM Notification n WHERE n.userId = :userId)")
    void deleteByUserId(@Param("userId") UUID userId);
}
