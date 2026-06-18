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
    name = "notification_deliveries",
    indexes = @Index(columnList = "notification_id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id", "device_token"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class NotificationDelivery extends BaseEntity {

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "device_token", nullable = false, length = 255)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSendStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Builder
    private NotificationDelivery(UUID notificationId, String deviceToken, DeviceType deviceType) {
        this.notificationId = notificationId;
        this.deviceToken = deviceToken;
        this.deviceType = deviceType;
        this.status = NotificationSendStatus.PENDING;
        this.attemptCount = 0;
    }

    public void markSuccess() {
        this.status = NotificationSendStatus.SUCCESS;
        this.attemptCount++;
    }

    public void markFailed() {
        this.status = NotificationSendStatus.FAILED;
        this.attemptCount++;
    }
}
