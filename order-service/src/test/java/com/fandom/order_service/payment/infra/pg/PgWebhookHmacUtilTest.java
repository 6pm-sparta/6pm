package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.infra.pg.mock.MockPgWebhookSigner;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgWebhookHmacUtil 단위 테스트")
class PgWebhookHmacUtilTest {

    private static final String SECRET = "test-pg-webhook-secret-key-at-least-32-bytes-long";

    private PgWebhookHmacUtil hmacUtil;
    // sign()은 MockPgWebhookSigner로 분리됐다. 테스트용 서명을 만들기 위해서만 사용.
    private MockPgWebhookSigner signer;

    @BeforeEach
    void setUp() {
        OrderProperties.PgWebhook pgWebhook = new OrderProperties.PgWebhook(SECRET, "http://localhost", 0L, 0L);
        OrderProperties properties = new OrderProperties(null, 0, null, null, null, null, null, null, pgWebhook);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        hmacUtil = new PgWebhookHmacUtil(properties, objectMapper);
        signer = new MockPgWebhookSigner(properties, objectMapper);
    }

    @Test
    @DisplayName("sign()으로 만든 서명은 동일한 요청에 대해 verify()를 통과한다")
    void signThenVerify_succeeds() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", UUID.randomUUID(), "APPROVED", 50_000L, null);

        // when
        String signature = signer.sign(request);

        // then
        assertThat(hmacUtil.verify(request, signature)).isTrue();
    }

    @Test
    @DisplayName("요청 내용이 하나라도 바뀌면 서명 검증에 실패한다(위변조 감지)")
    void verify_failsWhenPayloadTampered() {
        // given
        PgWebhookRequest original = new PgWebhookRequest("PG-1234", UUID.randomUUID(), "APPROVED", 50_000L, null);
        String signature = signer.sign(original);

        // when — amount만 다르게 바꿔서 검증(금액 위변조 시뮬레이션)
        PgWebhookRequest tampered = new PgWebhookRequest(
                original.pgTransactionId(), original.orderId(), original.status(), 999_999_999L, original.failureReason());

        // then
        assertThat(hmacUtil.verify(tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("서명 헤더가 없으면 검증에 실패한다")
    void verify_failsWhenSignatureMissing() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", UUID.randomUUID(), "APPROVED", 50_000L, null);

        // then
        assertThat(hmacUtil.verify(request, null)).isFalse();
        assertThat(hmacUtil.verify(request, "")).isFalse();
    }

    @Test
    @DisplayName("Base64 형식이 아닌 서명 헤더는 예외 없이 검증 실패로 처리한다")
    void verify_failsGracefullyOnInvalidBase64() {
        // given
        PgWebhookRequest request = new PgWebhookRequest("PG-1234", UUID.randomUUID(), "APPROVED", 50_000L, null);

        // then
        assertThat(hmacUtil.verify(request, "not-a-valid-base64-!!!")).isFalse();
    }
}
