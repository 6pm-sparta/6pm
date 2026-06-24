package com.fandom.aiops_service.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;


@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertWebhookRequest(
        String status,
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
