package com.fandom.aiops_service.infrastructure.messaging;

import com.fandom.aiops_service.application.event.IncidentDetectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * IncidentDetectedEvent → Kafka 중계기.
 *
 * 왜 AFTER_COMMIT 인가:
 *  - 웹훅 트랜잭션이 "커밋된 뒤"에만 Kafka로 보낸다. 그래야 컨슈머가 사건 row 를 확실히 읽을 수 있다.
 *    (커밋 전 publish 시 컨슈머가 아직 보이지 않는 row 를 조회하는 레이스 방지)
 *  - 메시지 페이로드는 incidentId(String) 하나 → 역직렬화 신뢰 패키지 설정 불필요, 컨슈머는 DB 재조회.
 *
 * 실패 처리:
 *  - 이미 200 응답 + DB 기록은 끝난 상태. 여기서 예외가 나도 웹훅 자체를 깨지 않도록 catch 후 ERROR 로그만.
 *    (LLM 분석 누락분은 추후 재처리/배치로 보강 가능 — #130)
 */
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
            // key=incidentId → 같은 사건은 같은 파티션 → 처리 순서 보장
            kafkaTemplate.send(topic, incidentId, incidentId);
            log.info("[AIOps] 분석 이벤트 발행 → topic={}, incidentId={}", topic, incidentId);
        } catch (Exception e) {
            log.error("[AIOps] 분석 이벤트 발행 실패 — incidentId={} (사건 기록은 보존됨)", incidentId, e);
        }
    }
}
