package com.fandom.user_service.member.infrastructure.kafka.outbox.domain;

/**
 * PENDING: 발행 대기(폴링 대상), PUBLISHED: 발행 완료.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
