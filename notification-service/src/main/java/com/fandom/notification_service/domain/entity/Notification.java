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
    name = "notifications",
    indexes = {
        @Index(columnList = "user_id"),
        @Index(name = "idx_notifications_status_created", columnList = "send_status, created_at")
    },
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "type", "reference_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, name = "is_read")
    private boolean read;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSendStatus sendStatus;

    @Builder
    private Notification(UUID userId, UUID referenceId, NotificationType type, String title, String body) {
        this.userId = userId;
        this.referenceId = referenceId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.read = false;
        this.sendStatus = NotificationSendStatus.PENDING;
    }

    public void markAsRead() {
        if (!this.read) {
            this.read = true;
        }
    }

    public void markAsSuccess() {
        this.sendStatus = NotificationSendStatus.SUCCESS;
    }

    public void markAsFailed() {
        this.sendStatus = NotificationSendStatus.FAILED;
    }
}
