package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.UserNotificationToken;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserNotificationTokenRepositoryImpl implements UserNotificationTokenRepository {

    private final UserNotificationTokenJpaRepository jpaRepository;

    @Override
    public UserNotificationToken save(UserNotificationToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public Optional<UserNotificationToken> findByDeviceToken(String deviceToken) {
        return jpaRepository.findByDeviceToken(deviceToken);
    }

    @Override
    public List<UserNotificationToken> findAllByUserIdAndNotifiedTrue(UUID userId) {
        return jpaRepository.findAllByUserIdAndNotifiedTrue(userId);
    }

    @Override
    public void deleteByDeviceToken(String deviceToken) {
        jpaRepository.deleteByDeviceToken(deviceToken);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
