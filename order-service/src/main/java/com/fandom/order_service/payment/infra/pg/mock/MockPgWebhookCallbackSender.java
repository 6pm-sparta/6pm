package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * MockPaymentGateway가 결정한 승인/환불 결과를 일정 시간 뒤 webhook으로 콜백 전송하는 역할만 담당한다.
 * 서명 생성·HTTP 전송이라는 인프라 관심사를 MockPaymentGateway(비즈니스 시나리오 결정)에서 분리했다.
 *
 * ThreadPoolTaskScheduler.schedule()로 지연을 구현한다 — 호출 스레드를 블로킹하지 않고
 * 지정 시각에 별도 풀 스레드에서 실행되도록 "예약"만 해두고 즉시 리턴한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPgWebhookCallbackSender {

    private final ThreadPoolTaskScheduler scheduler;
    private final RestClient restClient;
    private final MockPgWebhookSigner signer;
    private final OrderProperties orderProperties;

    public void sendDelayed(PgWebhookRequest payload) {
        sendDelayed(payload, 0L);
    }

    /**
     * extraDelayMillis: 기본 지연(callbackDelayMillis) 위에 추가로 얹는 지연(ms).
     */
    public void sendDelayed(PgWebhookRequest payload, long extraDelayMillis) {

        Instant fireAt = Instant.now().plusMillis(orderProperties.pgWebhook().callbackDelayMillis() + extraDelayMillis);

        scheduler.schedule(() -> this.send(payload), fireAt);

        log.info("[MockPG] webhook 콜백 예약. pgTransactionId={}, status={}, fireAt={}, extraDelayMillis={}",
                payload.pgTransactionId(), payload.status(), fireAt, extraDelayMillis);
    }

    private void send(PgWebhookRequest payload) {

        String signature = signer.sign(payload);

        try {
            restClient.post()
                    .uri(orderProperties.pgWebhook().callbackUrl())
                    .header("X-PG-Signature", signature)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[MockPG] webhook 콜백 전송 완료. pgTransactionId={}, status={}",
                    payload.pgTransactionId(), payload.status());

        } catch (Exception e) {
            // Mock. "한 번 쏘고 끝"으로 단순화한다.
            log.error("[MockPG] webhook 콜백 전송 실패. pgTransactionId={}", payload.pgTransactionId(), e);
        }
    }
}
