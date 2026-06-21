package com.fandom.notification_service.domain.repository;

import com.fandom.notification_service.domain.entity.UserNotificationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationTokenRepository {

    UserNotificationToken save(UserNotificationToken token);

    Optional<UserNotificationToken> findById(UUID id);

    Optional<UserNotificationToken> findByDeviceToken(String deviceToken);

    // 해당 유저 활성 토큰 목록
    List<UserNotificationToken> findAllByUserIdAndNotifiedTrue(UUID userId);

    // 단건 삭제 - 하드 삭제
    void deleteById(UUID id);

    // 무효 토큰 삭제 - 하드 삭제
    void deleteByDeviceToken(String deviceToken);

    // 유저 탈퇴 시 토큰 일괄 삭제 - 하드 삭제
    void deleteByUserId(UUID userId);
}
