package com.fandom.aiops_service.application;

import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.aiops_service.global.exception.AiopsErrorCode;
import com.fandom.aiops_service.infrastructure.slack.SlackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 분석 완료된 사건을 Slack 채널로 통보 (#129).
 *
 * 흐름(컨슈머가 analyzeAndStore 다음에 호출):
 *  - 사건 로드 → (분석 완료된 건만) 메시지 작성 → Slack 전송 → slack_ts(전송 마커) 저장.
 *
 * 설계:
 *  - 메서드 전체에 @Transactional 을 걸지 않는다. Slack HTTP 호출을 DB 트랜잭션 안에서 하지 않기 위함.
 *    (findById 는 자체 트랜잭션 읽기, 전송은 트랜잭션 밖, 마킹은 repository.save 의 자체 트랜잭션으로 처리)
 *  - 전송 실패해도 파이프라인을 깨지 않는다(graceful) — 도메인 ErrorCode 로 ERROR 로그.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final IncidentAlertHistoryRepository incidentRepository;
    private final SlackClient slackClient;

    public void notifyAnalysis(UUID incidentId) {
        if (!slackClient.isEnabled()) {
            log.warn("[AIOps] Slack webhook 미설정 — 통보 스킵: incidentId={}", incidentId);
            return;
        }

        IncidentAlertHistory incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            log.warn("[AIOps] 통보 대상 사건 없음 — 스킵: incidentId={}", incidentId);
            return;
        }
        if (!incident.isAiAnalyzed()) {
            // 분석이 아직/실패라 보낼 내용이 없음. (재처리 후 통보는 #130에서)
            log.debug("[AIOps] 미분석 사건 — Slack 통보 스킵: incidentId={}", incidentId);
            return;
        }

        try {
            slackClient.send(buildMessage(incident));      // 네트워크 호출 (트랜잭션 밖)
            incident.markSlackNotified(OffsetDateTime.now().toString());
            incidentRepository.save(incident);             // save() 자체 트랜잭션으로 slack_ts UPDATE
            log.info("[AIOps] Slack 통보 완료 → incidentId={}", incidentId);
        } catch (Exception e) {
            log.error("[AIOps] {} — incidentId={}", AiopsErrorCode.SLACK_NOTIFY_FAILED.getMessage(), incidentId, e);
        }
    }

    /** Slack mrkdwn 형식 메시지 작성 */
    private String buildMessage(IncidentAlertHistory incident) {
        return """
                :rotating_light: *[AIOps] 장애 분석* — `%s` %s @ %s

                *요약*: %s
                *추정 원인*: %s
                *대응 가이드*: %s

                _발생: %s_""".formatted(
                nullToDash(incident.getSeverity()),
                nullToDash(incident.getAlertName()),
                nullToDash(incident.getSourceService()),
                nullToDash(incident.getAiSummary()),
                nullToDash(incident.getAiRootCause()),
                nullToDash(incident.getAiGuide()),
                incident.getFiredAt()
        );
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
