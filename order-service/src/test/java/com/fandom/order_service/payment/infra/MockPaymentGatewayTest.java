package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockPaymentGateway 단위 테스트")
class MockPaymentGatewayTest {

    private final MockPaymentGateway mockPaymentGateway = new MockPaymentGateway();

    @Test
    @DisplayName("FAIL_/TIMEOUT_ 접두사가 없으면 결제를 승인하고 pgTransactionId를 발급한다")
    void requestApproval_default_returnsApproved() {
        // when
        PgApprovalResult result = mockPaymentGateway.requestApproval("normal-key", 50_000L, PaymentMethod.CARD);

        // then
        assertThat(result.isApproved()).isTrue();
        assertThat(result.pgTransactionId()).startsWith("PG-");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("idempotencyKey가 FAIL_로 시작하면 DECLINED를 반환하고 pgTransactionId는 비운다")
    void requestApproval_failPrefix_returnsDeclined() {
        // when
        PgApprovalResult result = mockPaymentGateway.requestApproval("FAIL_anything", 50_000L, PaymentMethod.CARD);

        // then
        assertThat(result.isApproved()).isFalse();
        assertThat(result.status()).isEqualTo(PgApprovalResult.PgResultStatus.DECLINED);
        assertThat(result.pgTransactionId()).isNull();
        assertThat(result.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("idempotencyKey가 TIMEOUT_로 시작하면 TIMEOUT을 반환한다")
    void requestApproval_timeoutPrefix_returnsTimeout() {
        // when
        PgApprovalResult result = mockPaymentGateway.requestApproval("TIMEOUT_anything", 50_000L, PaymentMethod.CARD);

        // then
        assertThat(result.status()).isEqualTo(PgApprovalResult.PgResultStatus.TIMEOUT);
        assertThat(result.isApproved()).isFalse();
        assertThat(result.pgTransactionId()).isNull();
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
}
