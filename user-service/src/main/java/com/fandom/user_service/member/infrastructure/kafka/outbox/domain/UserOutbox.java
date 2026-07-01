package com.fandom.user_service.member.infrastructure.kafka.outbox.domain;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox 레코드. 도메인 상태 변경과 같은 트랜잭션에서 INSERT해 이벤트 누락을 막는다.
 * 발행은 OutboxPublisher가 폴링으로 수행하며, at-least-once라 중복은 consumer 멱등성으로 흡수한다.
 *
 * id/createdAt은 BaseEntity가 관리한다(id는 UUIDv7 — 생성 시각 순이라 폴링이 created 순으로 읽기 좋다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_outbox", indexes = {
        @Index(name = "idx_user_outbox_status", columnList = "status")
})
public class UserOutbox extends BaseEntity {

    /** Kafka partition key로 사용(동일 aggregate 이벤트의 순서 보장 단위 — userId 또는 followId). */
    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 100)
    private String topic;

    /** 직렬화된 이벤트 JSON. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    private LocalDateTime publishedAt;

    @Builder
    private UserOutbox(UUID aggregateId, String topic, String payload) {
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
}
