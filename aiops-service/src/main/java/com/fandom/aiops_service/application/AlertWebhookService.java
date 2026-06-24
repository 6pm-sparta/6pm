package com.fandom.aiops_service.application;

import com.fandom.aiops_service.application.event.IncidentDetectedEvent;
import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
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

/**
 * Alertmanager 웹훅 처리:
 *  - firing  → 사건 신규 기록(DETECTED, fired_at) + 분석 이벤트 발행(#128)
 *  - resolved→ 진행 중 사건 찾아 복구 시각 + MTTR 기록
 *
 * 설계 포인트
 *  - 매칭 기준은 fingerprint 1순위(없으면 alertname+job 폴백) → firing↔resolved 짝짓기 정확도↑
 *  - 웹훅은 "기록"까지만 동기로 끝내고 200을 빠르게 반환한다. LLM 분석은 Kafka로 비동기 위임(#128)
 *    → 트랜잭션 커밋 이후(AFTER_COMMIT) Kafka publish (IncidentEventPublisher)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertWebhookService {

    private final IncidentAlertHistoryRepository incidentRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

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

        // 이미 진행 중인 동일 사건이 있으면 중복 생성 방지 (fingerprint 우선)
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

        // #128: LLM 분석을 비동기로 위임. 커밋 후 Kafka publish 되도록 도메인 이벤트만 발행.
        eventPublisher.publishEvent(new IncidentDetectedEvent(incident.getId()));
        // #129(후속): 분석 결과 Slack 통보(notification.send)
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
                        },
                        () -> log.warn("[AIOps] 해소 알림이지만 매칭되는 진행중 사건 없음: {} / {} / fp={}",
                                alertName, sourceService, fingerprint)
                );
    }

    /**
     * 진행 중(active) 사건 조회: fingerprint 가 있으면 그것으로, 없으면 alert_name+job 폴백.
     */
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
