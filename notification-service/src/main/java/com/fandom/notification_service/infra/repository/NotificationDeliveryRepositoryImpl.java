package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.NotificationDelivery;
import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryJpaRepository jpaRepository;

    @Override
    public NotificationDelivery save(NotificationDelivery delivery) {
        return jpaRepository.save(delivery);
    }

    @Override
    public Optional<NotificationDelivery> findByNotificationIdAndDeviceToken(UUID notificationId, String deviceToken) {
        return jpaRepository.findByNotificationIdAndDeviceToken(notificationId, deviceToken);
    }

    @Override
    public List<NotificationDelivery> findAllByNotificationId(UUID notificationId) {
        return jpaRepository.findAllByNotificationId(notificationId);
    }
}
