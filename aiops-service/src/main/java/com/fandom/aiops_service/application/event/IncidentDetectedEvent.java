package com.fandom.aiops_service.application.event;

import java.util.UUID;

/**
 * 사건 신규 기록(DETECTED) 시 발행되는 도메인 이벤트.
 * 상태가 아닌 "참조(incidentId)"만 싣는다 — 컨슈머가 DB에서 최신 상태를 다시 읽는다(단일 진실원).
 * AlertWebhookService(트랜잭션 내부)에서 발행 → IncidentEventPublisher 가 커밋 이후 Kafka로 중계.
 */
public record IncidentDetectedEvent(UUID incidentId) {
}
