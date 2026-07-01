package com.fandom.feed.infra.kafka.outbox;

public enum OutboxStatus {
    PENDING, PUBLISHED, FAILED
}