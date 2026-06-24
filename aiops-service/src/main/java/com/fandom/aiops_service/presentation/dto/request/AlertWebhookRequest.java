package com.fandom.aiops_service.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Alertmanager 웹훅 페이로드.
 * 우리가 쓰는 필드만 매핑하고 나머지(version, groupKey 등)는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertWebhookRequest(
        String status,            // 그룹 전체 상태 (firing / resolved)
        List<Alert> alerts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(
            String status,                  // 개별 알림 상태 (firing / resolved)
            Map<String, String> labels,     // alertname, severity, job, instance ...
            Map<String, String> annotations,// summary, description
            OffsetDateTime startsAt,        // 발생 시각
            OffsetDateTime endsAt,          // 해소 시각(미해소면 0001-...)
            String fingerprint
    ) {
        public String label(String key) {
            return labels == null ? null : labels.get(key);
        }
    }
}
