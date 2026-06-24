package com.fandom.aiops_service.infrastructure.messaging;

import com.fandom.aiops_service.application.IncidentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * incident.detected 토픽 컨슈머.
 *  - 페이로드는 incidentId(String) → DB에서 사건을 다시 읽어 LLM 분석을 수행/저장.
 *  - 분석 자체의 트랜잭션/예외 처리는 IncidentAnalysisService 가 담당(여기선 위임만).
 *  - 잘못된 메시지(파싱 불가 등)는 ERROR 로그 후 흘려보낸다(웹훅/적재는 이미 정상).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentAnalysisConsumer {

    private final IncidentAnalysisService analysisService;

    @KafkaListener(
            topics = "${aiops.kafka.topic.incident-detected:incident.detected}",
            groupId = "${spring.kafka.consumer.group-id:aiops-analysis}")
    public void onIncidentDetected(String incidentId) {
        log.info("[AIOps] 분석 이벤트 수신: incidentId={}", incidentId);
        try {
            analysisService.analyzeAndStore(UUID.fromString(incidentId));
        } catch (IllegalArgumentException e) {
            log.error("[AIOps] 잘못된 incidentId 메시지 — 스킵: {}", incidentId, e);
        }
    }
}
