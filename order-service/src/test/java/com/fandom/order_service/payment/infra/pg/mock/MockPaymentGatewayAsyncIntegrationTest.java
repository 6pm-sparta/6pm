package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.infra.pg.PgWebhookHmacUtil;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockPaymentGateway → MockPgWebhookCallbackSender가 설정된 지연 뒤 실제로 webhook 엔드포인트를
 * HTTP로 호출하는지 확인하는 통합 테스트.
 *
 * WireMock 등 외부 라이브러리를 새로 추가하지 않기 위해, 받는 쪽은 JDK 내장
 * com.sun.net.httpserver.HttpServer로 가볍게 띄운다. 모든 구성요소(스케줄러, RestClient, HMAC 유틸)는
 * Mock이 아니라 실제 객체를 사용한다 — "MockPaymentGateway가 실제로 HTTP 호출을 하는가"를
 * Mockito로 검증하는 단위 테스트(MockPaymentGatewayTest)와는 목적이 다르다.
 */
@DisplayName("MockPaymentGateway 비동기 webhook 콜백 통합 테스트")
class MockPaymentGatewayAsyncIntegrationTest {

    private static final String SECRET = "test-pg-webhook-secret-key-at-least-32-bytes-long";
    private static final long CALLBACK_DELAY_MILLIS = 100L;

    private HttpServer server;
    private ThreadPoolTaskScheduler scheduler;
    private MockPaymentGateway mockPaymentGateway;
    private PgWebhookHmacUtil hmacUtil;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CountDownLatch received = new CountDownLatch(1);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/webhook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(body, StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-PG-Signature"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            received.countDown();
        });
        server.start();

        int port = server.getAddress().getPort();
        OrderProperties properties = new OrderProperties(
                null, 0, null, null, null, null,
                new OrderProperties.PgWebhook(SECRET, "http://localhost:" + port + "/webhook", CALLBACK_DELAY_MILLIS, 600L));

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();

        hmacUtil = new PgWebhookHmacUtil(properties, objectMapper);
        MockPgWebhookSigner signer = new MockPgWebhookSigner(properties, objectMapper);
        MockPgWebhookCallbackSender sender = new MockPgWebhookCallbackSender(scheduler, RestClient.create(), signer, properties);
        // 이 테스트의 관심사는 webhook 실제 전송 여부이지 거래 영속화가 아니므로 mock으로 대체한다.
        mockPaymentGateway = new MockPaymentGateway(sender, Mockito.mock(MockPgTransactionRepository.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduler.destroy();
        server.stop(0);
    }

    @Test
    @DisplayName("승인 요청은 설정된 지연 뒤 서명이 포함된 APPROVED 콜백을 실제로 전송한다")
    void requestApprovalAsync_deliversSignedCallbackAfterDelay() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(orderId, "normal-key", 50_000L, PaymentMethod.CARD);

        // then — 콜백 지연(100ms)보다 충분히 긴 시간 동안 대기
        assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();

        PgWebhookRequest payload = objectMapper.readValue(receivedBody.get(), PgWebhookRequest.class);
        assertThat(payload.pgTransactionId()).isEqualTo(pgTransactionId);
        assertThat(payload.orderId()).isEqualTo(orderId);
        assertThat(payload.status()).isEqualTo("APPROVED");

        // 수신측에서 같은 secret으로 서명을 검증할 수 있어야 한다(실제 PgWebhookController가 하는 일과 동일)
        assertThat(hmacUtil.verify(payload, receivedSignature.get())).isTrue();
    }

    @Test
    @DisplayName("TIMEOUT_ 접두사 요청은 webhook 콜백을 전혀 보내지 않는다")
    void requestApprovalAsync_timeoutPrefix_neverDeliversCallback() throws Exception {
        // when
        mockPaymentGateway.requestApprovalAsync(UUID.randomUUID(), "TIMEOUT_anything", 50_000L, PaymentMethod.CARD);

        // then — 지연(100ms)이 지나고도 한참 더 기다려도 콜백이 오지 않아야 함
        assertThat(received.await(500, TimeUnit.MILLISECONDS)).isFalse();
    }
}
