package com.fandom.aiops_service.infrastructure.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Slack Incoming Webhook 전송기 (#129).
 *  - 채널에 텍스트 메시지를 POST 한다(페이로드: {"text": "..."}).
 *  - webhook-url 이 비어있으면(미설정) 전송을 건너뛴다 → 키 없는 다른 개발자도 앱 기동/동작 가능.
 *  - 전송 실패(4xx/5xx/네트워크)는 RestClient 가 예외를 던지고, 호출부(SlackNotificationService)가 graceful 처리.
 *  - Incoming Webhook 은 메시지 ts 를 응답으로 주지 않는다(스레드가 필요하면 Web API 로 업그레이드).
 */
@Slf4j
@Component
public class SlackClient {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackClient(RestClient.Builder builder,
                       @Value("${aiops.slack.webhook-url:}") String webhookUrl) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    /** webhook-url 이 설정돼 있는지 (미설정이면 통보 스킵) */
    public boolean isEnabled() {
        return StringUtils.hasText(webhookUrl);
    }

    /** 텍스트 메시지 전송. 실패 시 예외를 던진다(호출부에서 graceful 처리). */
    public void send(String text) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
