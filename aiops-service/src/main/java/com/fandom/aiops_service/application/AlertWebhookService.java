package com.fandom.aiops_service.application;

import com.fandom.aiops_service.application.event.IncidentDetectedEvent;
import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.aiops_service.infrastructure.metrics.IncidentMetrics;
import com.fandom.aiops_service.presentation.dto.request.AlertWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertWebhookService {

    private final IncidentAlertHistoryRepository incidentRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IncidentMetrics incidentMetrics;

    @Transactional
    public void handleWebhook(AlertWebhookRequest request) {
        if (request == null || request.alerts() == null || request.alerts().isEmpty()) {
            log.warn("[AIOps] 빈 웹훅 페이로드 수신 — 무시");
            return;
        }
        for (AlertWebhookRequest.Alert alert : request.alerts()) {
            if ("resolved".equalsIgnoreCase(alert.status())) {
                resolveIncident(alert);
            } else {
                detectIncident(alert);
            }
        }
    }

    private void detectIncident(AlertWebhookRequest.Alert alert) {
        String alertName = alert.label("alertname");
        String severity = alert.label("severity");
        String sourceService = alert.label("job");
        String fingerprint = alert.fingerprint();
        OffsetDateTime firedAt = alert.startsAt() != null ? alert.startsAt() : OffsetDateTime.now();

        if (findActiveIncident(fingerprint, alertName, sourceService).isPresent()) {
            log.debug("[AIOps] 이미 진행 중인 사건 — 스킵: {} / {} / fp={}", alertName, sourceService, fingerprint);
            return;
        }

        IncidentAlertHistory incident = IncidentAlertHistory.builder()
                .alertName(alertName)
                .severity(severity)
                .sourceService(sourceService)
                .fingerprint(fingerprint)
                .firedAt(firedAt)
                .rawPayload(toJson(alert))
                .build();
        incidentRepository.save(incident);
        log.info("[AIOps] 사건 기록(DETECTED): {} / {} / {} / fp={}", alertName, severity, sourceService, fingerprint);
        incidentMetrics.recordDetected(sourceService, severity);   // #130 집계

        eventPublisher.publishEvent(new IncidentDetectedEvent(incident.getId()));
    }

    private void resolveIncident(AlertWebhookRequest.Alert alert) {
        String alertName = alert.label("alertname");
        String sourceService = alert.label("job");
        String fingerprint = alert.fingerprint();
        OffsetDateTime resolvedAt = alert.endsAt() != null ? alert.endsAt() : OffsetDateTime.now();

        findActiveIncident(fingerprint, alertName, sourceService)
                .ifPresentOrElse(
                        incident -> {
                            incident.resolve(resolvedAt);   // 더티체킹으로 update
                            log.info("[AIOps] 사건 해소(RESOLVED): {} / {} / MTTR {}s",
                                    alertName, sourceService, incident.getMttrSeconds());
                            incidentMetrics.recordResolved(   // #130 집계 (MTTR 기록)
                                    incident.getSourceService(), incident.getSeverity(), incident.getMttrSeconds());
                        },
                        () -> log.warn("[AIOps] 해소 알림이지만 매칭되는 진행중 사건 없음: {} / {} / fp={}",
                                alertName, sourceService, fingerprint)
                );
    }

    private Optional<IncidentAlertHistory> findActiveIncident(String fingerprint,
                                                              String alertName, String sourceService) {
        if (StringUtils.hasText(fingerprint)) {
            return incidentRepository.findFirstByFingerprintAndResolvedAtIsNullOrderByFiredAtDesc(fingerprint);
        }
        return incidentRepository
                .findFirstByAlertNameAndSourceServiceAndResolvedAtIsNullOrderByFiredAtDesc(alertName, sourceService);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[AIOps] rawPayload 직렬화 실패", e);
            return null;
        }
    }
}