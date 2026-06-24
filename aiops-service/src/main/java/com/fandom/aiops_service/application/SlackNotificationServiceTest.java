package com.fandom.aiops_service.application;

import com.fandom.aiops_service.domain.entity.IncidentAlertHistory;
import com.fandom.aiops_service.domain.repository.IncidentAlertHistoryRepository;
import com.fandom.aiops_service.infrastructure.slack.SlackClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Slack 통보 서비스 단위 테스트.
 *  - 분석 완료 + webhook 설정 시: 전송 후 slack_ts 마킹.
 *  - 전송 실패 시: graceful(예외 전파 X, slack_ts 미설정).
 *  - 미분석/미설정 시: 전송 시도 안 함(스킵).
 */
class SlackNotificationServiceTest {

    private final IncidentAlertHistoryRepository repository = mock(IncidentAlertHistoryRepository.class);
    private final SlackClient slackClient = mock(SlackClient.class);
    private final SlackNotificationService service = new SlackNotificationService(repository, slackClient);

    private IncidentAlertHistory analyzedIncident() {
        IncidentAlertHistory incident = IncidentAlertHistory.builder()
                .alertName("HighErrorRate").severity("critical").sourceService("ticketing-service")
                .fingerprint("fp-1").firedAt(OffsetDateTime.parse("2026-06-24T05:00:00Z"))
                .rawPayload("{}").build();
        incident.applyAiAnalysis("요약", "원인", "가이드");   // 분석 완료 상태
        return incident;
    }

    @Test
    @DisplayName("성공: 전송 후 slack_ts 마킹 + save")
    void notify_success() {
        UUID id = UUID.randomUUID();
        IncidentAlertHistory incident = analyzedIncident();
        when(slackClient.isEnabled()).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(incident));

        service.notifyAnalysis(id);

        verify(slackClient).send(anyString());
        verify(repository).save(incident);
        assertThat(incident.getSlackTs()).isNotNull();
    }

    @Test
    @DisplayName("graceful: 전송 실패해도 예외 없이 slack_ts 미설정")
    void notify_sendFailure_isGraceful() {
        UUID id = UUID.randomUUID();
        IncidentAlertHistory incident = analyzedIncident();
        when(slackClient.isEnabled()).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(incident));
        doThrow(new RuntimeException("slack down")).when(slackClient).send(anyString());

        service.notifyAnalysis(id);   // 예외 전파 X

        assertThat(incident.getSlackTs()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("스킵: webhook 미설정이면 전송 시도 안 함")
    void notify_disabled_skips() {
        when(slackClient.isEnabled()).thenReturn(false);

        service.notifyAnalysis(UUID.randomUUID());

        verify(slackClient, never()).send(anyString());
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("스킵: 미분석 사건은 통보하지 않음")
    void notify_notAnalyzed_skips() {
        UUID id = UUID.randomUUID();
        IncidentAlertHistory notAnalyzed = IncidentAlertHistory.builder()
                .alertName("PodDown").severity("warning").sourceService("feed-service")
                .fingerprint("fp-2").firedAt(OffsetDateTime.parse("2026-06-24T06:00:00Z"))
                .rawPayload("{}").build();
        when(slackClient.isEnabled()).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(notAnalyzed));

        service.notifyAnalysis(id);

        verify(slackClient, never()).send(anyString());
    }
}
