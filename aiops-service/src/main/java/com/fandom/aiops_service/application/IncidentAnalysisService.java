package com.fandom.aiops_service.application;

import com.fandom.aiops_service.application.dto.AiAnalysisResult;
import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.common.exception.CustomException;
import com.fandom.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

        try {
            AiAnalysisResult result = analyze(incident);
            incident.applyAiAnalysis(result.summary(), result.rootCause(), result.guide());
            log.info("[AIOps] LLM 분석 완료 → incidentId={}, summary={}", incidentId, result.summary());
        } catch (CustomException e) {
            // graceful: 분석에 실패해도 사건 기록은 보존(미분석 상태). 추후 재처리 가능(#130).
            log.warn("[AIOps] 분석 미완료 — 미분석 보존: incidentId={}, code={}", incidentId, e.getErrorCode());
        }
    }

    public AiAnalysisResult analyze(IncidentAlertHistory incident) {
        try {
            return chatClient.prompt()
                    .user(buildUserPrompt(incident))
                    .call()
                    .entity(AiAnalysisResult.class);
        } catch (Exception e) {
            log.error("[AIOps] {} — incidentId={}", ErrorCode.LLM_ANALYSIS_FAILED.getMessage(), incident.getId(), e);
            throw new CustomException(ErrorCode.LLM_ANALYSIS_FAILED);
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
