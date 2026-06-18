package com.fandom.notification_service.domain.repository;

import com.fandom.notification_service.domain.entity.NotificationDelivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository {

    NotificationDelivery save(NotificationDelivery delivery);

    Optional<NotificationDelivery> findByNotificationIdAndDeviceToken(UUID notificationId, String deviceToken);

    List<NotificationDelivery> findAllByNotificationId(UUID notificationId);
}
