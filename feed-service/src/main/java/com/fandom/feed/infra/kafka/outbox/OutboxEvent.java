package com.fandom.feed.infra.kafka.outbox;

import com.fandom.feed.domain.entity.SimpleBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_event_status_id", columnList = "status, id")
        }
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends SimpleBaseEntity {
    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int retryCount;

    private LocalDateTime publishedAt;

    @Builder
    private OutboxEvent(UUID aggregateId, OutboxEventType eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent of(UUID aggregateId, OutboxEventType eventType, String payload) {
        return OutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.retryCount++;
        this.status = retryCount >= 3 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public void markFailedImmediately() {
        this.status = OutboxStatus.FAILED;
    }
}