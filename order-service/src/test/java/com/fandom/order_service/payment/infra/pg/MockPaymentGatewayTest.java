package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockPaymentGateway 단위 테스트")
class MockPaymentGatewayTest {

    @Mock
    private PgWebhookCallbackSender callbackSender;

    private MockPaymentGateway mockPaymentGateway;

    @BeforeEach
    void setUp() {
        mockPaymentGateway = new MockPaymentGateway(callbackSender);
    }

    @Test
    @DisplayName("pgTransactionId가 있으면 환불에 성공한다")
    void requestRefund_withTransactionId_returnsSuccess() {
        // when
        PgRefundResult result = mockPaymentGateway.requestRefund("PG-1234", 50_000L);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("pgTransactionId가 없으면 환불에 실패한다")
    void requestRefund_withoutTransactionId_returnsFailure() {
        // when
        PgRefundResult result = mockPaymentGateway.requestRefund(null, 50_000L);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("비동기 승인 요청 - 기본값이면 pgTransactionId를 즉시 반환하고 APPROVED 콜백을 예약한다")
    void requestApprovalAsync_default_schedulesApprovedCallback() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(orderId, "normal-key", 50_000L, PaymentMethod.CARD);

        // then
        assertThat(pgTransactionId).startsWith("PG-");

        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender).sendDelayed(captor.capture());

        PgWebhookRequest payload = captor.getValue();
        assertThat(payload.pgTransactionId()).isEqualTo(pgTransactionId);
        assertThat(payload.orderId()).isEqualTo(orderId);
        assertThat(payload.status()).isEqualTo("APPROVED");
        assertThat(payload.amount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("비동기 승인 요청 - FAIL_ 접두사면 FAILED 콜백을 예약한다")
    void requestApprovalAsync_failPrefix_schedulesFailedCallback() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        mockPaymentGateway.requestApprovalAsync(orderId, "FAIL_anything", 50_000L, PaymentMethod.CARD);

        // then
        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender).sendDelayed(captor.capture());

        assertThat(captor.getValue().status()).isEqualTo("FAILED");
        assertThat(captor.getValue().failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("비동기 승인 요청 - TIMEOUT_ 접두사면 콜백을 전혀 보내지 않는다(webhook 미수신 시뮬레이션)")
    void requestApprovalAsync_timeoutPrefix_neverSendsCallback() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(orderId, "TIMEOUT_anything", 50_000L, PaymentMethod.CARD);

        // then
        assertThat(pgTransactionId).startsWith("PG-"); // 접수 자체는 됨(거래 식별자는 발급)
        verify(callbackSender, never()).sendDelayed(any());
    }

    @Test
    @DisplayName("비동기 환불 요청 - pgTransactionId가 있으면 REFUNDED 콜백을 예약한다")
    void requestRefundAsync_withTransactionId_schedulesRefundedCallback() {
        // given
        UUID orderId = UUID.randomUUID();

        // when
        mockPaymentGateway.requestRefundAsync(orderId, "PG-1234", 50_000L);

        // then
        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender).sendDelayed(captor.capture());

        assertThat(captor.getValue().status()).isEqualTo("REFUNDED");
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("비동기 환불 요청 - pgTransactionId가 없으면 콜백을 보내지 않는다")
    void requestRefundAsync_withoutTransactionId_neverSendsCallback() {
        // when
        mockPaymentGateway.requestRefundAsync(UUID.randomUUID(), null, 50_000L);

        // then
        verify(callbackSender, never()).sendDelayed(any());
    }
}