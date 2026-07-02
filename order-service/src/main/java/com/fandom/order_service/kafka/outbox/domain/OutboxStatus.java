package com.fandom.order_service.kafka.outbox.domain;

/**
 * PENDING: 발행 대기(폴링 대상), PUBLISHED: 발행 완료, FAILED: 재시도 소진(수동 처리 대상).
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
