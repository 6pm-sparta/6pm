package com.fandom.notification_service.domain.repository;

import com.fandom.notification_service.domain.entity.NotificationDelivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository {

    NotificationDelivery save(NotificationDelivery delivery);

    Optional<NotificationDelivery> findByNotificationIdAndDeviceToken(UUID notificationId, String deviceToken);

    List<NotificationDelivery> findAllByNotificationId(UUID notificationId);

    // 탈퇴 시 해당 유저의 알림의 전달 레코드 하드 삭제 -> divice token 제거
    void deleteByUserId(UUID userId);
}
