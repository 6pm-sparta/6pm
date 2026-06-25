package com.fandom.aiops_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "incident_alert_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentAlertHistory extends BaseEntity {

    @Column(name = "alert_name", nullable = false, length = 200)
    private String alertName;            // Alertmanager rule 이름

    @Column(length = 20)
    private String severity;             // critical / warning / info

    @Column(name = "source_service", length = 100)
    private String sourceService;        // 장애 발생 서비스(job)

    @Column(name = "fingerprint", length = 255)
    private String fingerprint;          // Alertmanager가 부여하는 알림 시리즈 고유키(중복/해소 매칭 기준)

    @Column(name = "fired_at", nullable = false)
    private OffsetDateTime firedAt;      // 알림 발생 시각 (MTTR 시작점)

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;   // 해소 시각 (null=진행중)

    @Column(name = "mttr_seconds")
    private Long mttrSeconds;            // resolved_at - fired_at (초)

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;            // LLM 분석 결과 (#128에서 채움)

    @Column(name = "ai_root_cause", columnDefinition = "text")
    private String aiRootCause;

    @Column(name = "ai_guide", columnDefinition = "text")
    private String aiGuide;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;           // 원본 알림 JSON

    @Column(name = "slack_ts", length = 50)
    private String slackTs;              // Slack 메시지 추적 (#129에서 채움)

    @Builder
    private IncidentAlertHistory(String alertName, String severity, String sourceService,

                                 String fingerprint, OffsetDateTime firedAt, String rawPayload) {
        this.alertName = alertName;
        this.severity = severity;
        this.sourceService = sourceService;
        this.fingerprint = fingerprint;

        this.firedAt = firedAt;
        this.rawPayload = rawPayload;
    }

    /** LLM 분석이 이미 채워졌는지 — 중복 분석 방지용 */
    public boolean isAiAnalyzed() {
        return this.aiSummary != null;
    }


    /** resolved 알림 수신 시: 복구 시각 + MTTR(초) 기록 */
    public void resolve(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
        if (this.firedAt != null && resolvedAt != null) {
            this.mttrSeconds = Duration.between(this.firedAt, resolvedAt).getSeconds();
        }
    }

    /** LLM 분석 결과 채우기 (#128에서 사용) */

    public void applyAiAnalysis(String summary, String rootCause, String guide) {
        this.aiSummary = summary;
        this.aiRootCause = rootCause;
        this.aiGuide = guide;
    }
}
