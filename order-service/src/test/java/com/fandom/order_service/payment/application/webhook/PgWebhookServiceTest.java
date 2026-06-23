package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PgWebhookHmacUtil;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PgWebhookService 단위 테스트")
class PgWebhookServiceTest {

    @Mock
    private PgWebhookHmacUtil signatureVerifier;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final OrderProperties orderProperties = new OrderProperties(
            null, 0, null, null, null,
            new OrderProperties.PgWebhook("secret", "http://localhost", 0L, 600L));

    private PgWebhookService pgWebhookService;

    private final PgWebhookRequest request =
            new PgWebhookRequest("PG-1234", UUID.randomUUID(), "APPROVED", 50_000L, null);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        pgWebhookService = new PgWebhookService(signatureVerifier, redisTemplate, orderProperties);
    }

    @Test
    @DisplayName("서명 검증에 실패하면 INVALID_SIGNATURE 예외를 던진다")
    void receive_invalidSignature_throws() {
        // given
        given(signatureVerifier.verify(request, "bad-signature")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> pgWebhookService.receive(request, "bad-signature"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_SIGNATURE);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("서명 검증을 통과하고 신규 pgTransactionId면 정상 처리되고 예외가 없다")
    void receive_validAndNew_completesWithoutError() {
        // given
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(true);

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 처리된 pgTransactionId(중복 수신)면 조용히 무시하고 예외가 없다")
    void receive_duplicatePgTransactionId_silentlyIgnored() {
        // given
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any())).willReturn(false); // 이미 누가 선점함

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 장애 시 중복수신 1차 방어를 건너뛰고 통과시킨다(예외를 던지지 않음)")
    void receive_redisDown_fallsThroughWithoutError() {
        // given
        given(signatureVerifier.verify(request, "good-signature")).willReturn(true);
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("redis down"));

        // when & then
        assertThatCode(() -> pgWebhookService.receive(request, "good-signature")).doesNotThrowAnyException();
    }
}
