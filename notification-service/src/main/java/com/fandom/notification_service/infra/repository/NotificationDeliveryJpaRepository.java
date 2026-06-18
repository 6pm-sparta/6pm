package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findByNotificationIdAndDeviceToken(UUID notificationId, String deviceToken);

    List<NotificationDelivery> findAllByNotificationId(UUID notificationId);
}
