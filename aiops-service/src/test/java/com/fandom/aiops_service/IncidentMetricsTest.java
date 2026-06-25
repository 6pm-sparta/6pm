package com.fandom.aiops_service;

import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.aiops_service.infrastructure.metrics.IncidentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 집계 메트릭 단위 테스트 — SimpleMeterRegistry 로 카운터/타이머/게이지 검증.
 */
class IncidentMetricsTest {

    @Test
    @DisplayName("detected/resolved 카운터, MTTR 타이머, active 게이지가 기록된다")
    void recordsMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IncidentAlertHistoryRepository repo = mock(IncidentAlertHistoryRepository.class);
        when(repo.countByResolvedAtIsNull()).thenReturn(2L);

        IncidentMetrics metrics = new IncidentMetrics(registry, repo);
        metrics.recordDetected("ticketing-service", "critical");
        metrics.recordResolved("ticketing-service", "critical", 300L);

        assertThat(registry.get("aiops.incident.detected")
                .tags("service", "ticketing-service", "severity", "critical").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("aiops.incident.resolved").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("aiops.incident.mttr").timer().totalTime(TimeUnit.SECONDS))
                .isEqualTo(300.0);
        assertThat(registry.get("aiops.incident.active").gauge().value()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("service/severity 가 null 이면 unknown 태그로 기록된다")
    void nullTagsBecomeUnknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IncidentAlertHistoryRepository repo = mock(IncidentAlertHistoryRepository.class);

        IncidentMetrics metrics = new IncidentMetrics(registry, repo);
        metrics.recordDetected(null, null);

        assertThat(registry.get("aiops.incident.detected")
                .tags("service", "unknown", "severity", "unknown").counter().count())
                .isEqualTo(1.0);
    }
}
