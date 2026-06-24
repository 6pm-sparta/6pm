package com.fandom.aiops_service.infrastructure.messaging;

import com.fandom.aiops_service.application.event.IncidentDetectedEvent;
import com.fandom.aiops_service.global.exception.AiopsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class IncidentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public IncidentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${aiops.kafka.topic.incident-detected:incident.detected}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(IncidentDetectedEvent event) {
        String incidentId = event.incidentId().toString();
        try {
            kafkaTemplate.send(topic, incidentId, incidentId);
            log.info("[AIOps] 분석 이벤트 발행 → topic={}, incidentId={}", topic, incidentId);
        } catch (Exception e) {
            log.error("[AIOps] {} — incidentId={} (사건 기록은 보존됨)",
                    AiopsErrorCode.INCIDENT_EVENT_PUBLISH_FAILED.getMessage(), incidentId, e);
        }
    }
}
