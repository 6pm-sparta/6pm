package com.fandom.feed.infra.kafka.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {
    @Id
    private String eventKey;
    private LocalDateTime processedAt;

    @Builder
    private ProcessedEvent(String eventKey) {
        this.eventKey = eventKey;
        this.processedAt = LocalDateTime.now();
    }
}