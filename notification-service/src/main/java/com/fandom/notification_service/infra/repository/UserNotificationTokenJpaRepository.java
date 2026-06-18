package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.UserNotificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationTokenJpaRepository extends JpaRepository<UserNotificationToken, UUID> {

    Optional<UserNotificationToken> findByDeviceToken(String deviceToken);

    List<UserNotificationToken> findAllByUserIdAndNotifiedTrue(UUID userId);

    List<UserNotificationToken> findAllByUserId(UUID userId);
}
