package com.fandom.notification_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(
    name = "user_notification_tokens",
    indexes = @Index(columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
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

    // 토큰 갱신
    public void updateToken(String newToken) {
        this.deviceToken = newToken;
    }

    // 알림 설정
    public void toggleNotification(boolean notified) {
        this.notified = notified;
    }
}
