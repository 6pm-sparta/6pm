package com.fandom.notification_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
    name = "user_notification_tokens",
    indexes = @Index(columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(nullable = false, name = "is_notified")
    private boolean notified;

    @Builder
    private UserNotificationToken(UUID userId, String deviceToken, DeviceType deviceType) {
        this.userId = userId;
        this.deviceToken = deviceToken;
        this.deviceType = deviceType;
        this.notified = true;
    }

    // 등록시 기존 토큰 행의 소유자/타입 갱신, 수신 ON
    public void reassign(UUID userId, DeviceType deviceType) {
        this.userId = userId;
        this.deviceType = deviceType;
        this.notified = true;
    }

    // 알림 설정
    public void toggleNotification(boolean notified) {
        this.notified = notified;
    }
}
