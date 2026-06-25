package com.fandom.aiops_service.infrastructure.metrics;

import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
public class IncidentMetrics {

    private final MeterRegistry registry;

    public IncidentMetrics(MeterRegistry registry, IncidentAlertHistoryRepository repository) {
        this.registry = registry;
        // 진행중 사건 수: 스크랩 시점에 DB count 를 읽는 gauge
        Gauge.builder("aiops.incident.active", repository,
                        r -> (double) r.countByResolvedAtIsNull())
                .description("현재 미해소(진행중) 사건 수")
                .register(registry);
    }

    public void recordDetected(String service, String severity) {
        registry.counter("aiops.incident.detected", "service", nz(service), "severity", nz(severity))
                .increment();
    }

    public void recordResolved(String service, String severity, Long mttrSeconds) {
        registry.counter("aiops.incident.resolved", "service", nz(service), "severity", nz(severity))
                .increment();
        if (mttrSeconds != null) {
            registry.timer("aiops.incident.mttr", "service", nz(service), "severity", nz(severity))
                    .record(Duration.ofSeconds(mttrSeconds));
        }
    }

    private String nz(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s;
    }
}
