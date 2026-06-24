package com.fandom.aiops_service.application;

import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.aiops_service.presentation.dto.request.AlertWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertWebhookService {

    private final IncidentAlertHistoryRepository incidentRepository;
    private final ObjectMapper objectMapper;

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
        OffsetDateTime firedAt = alert.startsAt() != null ? alert.startsAt() : OffsetDateTime.now();

        boolean alreadyActive = incidentRepository
                .findFirstByAlertNameAndSourceServiceAndResolvedAtIsNullOrderByFiredAtDesc(alertName, sourceService)
                .isPresent();
        if (alreadyActive) {
            log.debug("[AIOps] 이미 진행 중인 사건 — 스킵: {} / {}", alertName, sourceService);
            return;
        }

        IncidentAlertHistory incident = IncidentAlertHistory.builder()
                .alertName(alertName)
                .severity(severity)
                .sourceService(sourceService)
                .firedAt(firedAt)
                .rawPayload(toJson(alert))
                .build();
        incidentRepository.save(incident);
        log.info("[AIOps] 사건 기록(DETECTED): {} / {} / {}", alertName, severity, sourceService);
        // TODO(#128): 여기서 LLM 분석 호출 → incident.applyAiAnalysis(...)
        // TODO(#129): 분석 결과 Slack 통보(notification.send)
    }

    private void resolveIncident(AlertWebhookRequest.Alert alert) {
        String alertName = alert.label("alertname");
        String sourceService = alert.label("job");
        OffsetDateTime resolvedAt = alert.endsAt() != null ? alert.endsAt() : OffsetDateTime.now();

        incidentRepository
                .findFirstByAlertNameAndSourceServiceAndResolvedAtIsNullOrderByFiredAtDesc(alertName, sourceService)
                .ifPresentOrElse(
                        incident -> {
                            incident.resolve(resolvedAt);
                            log.info("[AIOps] 사건 해소(RESOLVED): {} / {} / MTTR {}s",
                                    alertName, sourceService, incident.getMttrSeconds());
                        },
                        () -> log.warn("[AIOps] 해소 알림이지만 매칭되는 진행중 사건 없음: {} / {}", alertName, sourceService)
                );
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
