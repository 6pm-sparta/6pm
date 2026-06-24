package com.fandom.aiops_service.application;

import com.fandom.aiops_service.application.dto.AiAnalysisResult;
import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 사건(incident)에 대한 LLM(Gemini) 분석 → DB 저장.
 * Kafka 컨슈머(IncidentAnalysisConsumer)가 호출하는 비동기 경로의 핵심 로직.
 *
 * 설계:
 *  - analyzeAndStore 는 @Transactional. 사건을 로드 → LLM 분석 → applyAiAnalysis(더티체킹 update).
 *  - LLM 호출 실패는 graceful: ERROR 로그만 남기고 사건은 미분석 상태로 둔다(이후 재처리 가능).
 *  - 이미 분석된 사건은 멱등하게 스킵(중복 메시지/재처리 대비).
 */
@Slf4j
@Service
public class IncidentAnalysisService {

    private final IncidentAlertHistoryRepository incidentRepository;
    private final ChatClient chatClient;

    public IncidentAnalysisService(IncidentAlertHistoryRepository incidentRepository,
                                   ChatClient incidentAnalysisChatClient) {
        this.incidentRepository = incidentRepository;
        this.chatClient = incidentAnalysisChatClient;
    }

    @Transactional
    public void analyzeAndStore(UUID incidentId) {
        IncidentAlertHistory incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) {
            log.warn("[AIOps] 분석 대상 사건 없음 — 스킵: incidentId={}", incidentId);
            return;
        }
        if (incident.isAiAnalyzed()) {
            log.debug("[AIOps] 이미 분석된 사건 — 스킵(멱등): incidentId={}", incidentId);
            return;
        }

        analyze(incident).ifPresentOrElse(
                result -> {
                    incident.applyAiAnalysis(result.summary(), result.rootCause(), result.guide());
                    log.info("[AIOps] LLM 분석 완료 → incidentId={}, summary={}", incidentId, result.summary());
                },
                () -> log.warn("[AIOps] LLM 분석 결과 없음 — 미분석 보존: incidentId={}", incidentId)
        );
    }

    /**
     * 실제 LLM 호출. 실패 시 Optional.empty() (예외를 밖으로 던지지 않는다).
     */
    public Optional<AiAnalysisResult> analyze(IncidentAlertHistory incident) {
        try {
            AiAnalysisResult result = chatClient.prompt()
                    .user(buildUserPrompt(incident))
                    .call()
                    .entity(AiAnalysisResult.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("[AIOps] LLM 분석 호출 실패 — incidentId={}", incident.getId(), e);
            return Optional.empty();
        }
    }

    private String buildUserPrompt(IncidentAlertHistory incident) {
        String raw = incident.getRawPayload();
        if (raw != null && raw.length() > 4000) {   // 토큰/비용 방어: 원본이 너무 크면 절단
            raw = raw.substring(0, 4000) + "...(생략)";
        }
        return """
                다음 장애 알림을 분석해줘.

                - 알림명(alertName): %s
                - 심각도(severity): %s
                - 발생 서비스(sourceService): %s
                - 발생시각(firedAt): %s
                - 원본 payload(JSON):
                %s
                """.formatted(
                incident.getAlertName(),
                incident.getSeverity(),
                incident.getSourceService(),
                incident.getFiredAt(),
                raw == null ? "(없음)" : raw
        );
    }
}
