package com.fandom.aiops_service.infrastructure.messaging;

import com.fandom.aiops_service.application.IncidentAnalysisService;
import com.fandom.aiops_service.application.SlackNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentAnalysisConsumer {

    private final IncidentAnalysisService analysisService;
    private final SlackNotificationService slackNotificationService;

    @KafkaListener(
            topics = "${aiops.kafka.topic.incident-detected:incident.detected}",
            groupId = "${spring.kafka.consumer.group-id:aiops-analysis}")
    public void onIncidentDetected(String incidentId) {
        log.info("[AIOps] 분석 이벤트 수신: incidentId={}", incidentId);
        final UUID id;
        try {
            id = UUID.fromString(incidentId);
        } catch (IllegalArgumentException e) {
            log.error("[AIOps] 잘못된 incidentId 메시지 — 스킵: {}", incidentId, e);
            return;
        }
        analysisService.analyzeAndStore(id);
        slackNotificationService.notifyAnalysis(id);
    }
}
