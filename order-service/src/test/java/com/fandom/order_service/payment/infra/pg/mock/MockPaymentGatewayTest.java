package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.infra.pg.PgTransactionResult;
import com.fandom.order_service.payment.infra.pg.PgTransactionStatus;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockPaymentGateway 단위 테스트")
class MockPaymentGatewayTest {

    @Mock
    private MockPgWebhookCallbackSender callbackSender;

    @Mock
    private MockPgTransactionRepository mockPgTransactionRepository;

    private MockPaymentGateway mockPaymentGateway;

    @BeforeEach
    void setUp() {
        mockPaymentGateway = new MockPaymentGateway(callbackSender, mockPgTransactionRepository);
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
    @DisplayName("비동기 승인 요청 - 결과와 무관하게 PG 자체 거래 기록을 영속화한다")
    void requestApprovalAsync_persistsGroundTruthTransaction() {
        // given
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<MockPgTransaction> savedCaptor = ArgumentCaptor.forClass(MockPgTransaction.class);
        given(mockPgTransactionRepository.save(savedCaptor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(orderId, "normal-key", 50_000L, PaymentMethod.CARD);

        // then
        MockPgTransaction saved = savedCaptor.getValue();
        assertThat(saved.getPgTransactionId()).isEqualTo(pgTransactionId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getStatus()).isEqualTo(PgTransactionResult.APPROVED);
    }

    @Test
    @DisplayName("비동기 승인 요청 - TIMEOUT_ 접두사여도 webhook과 무관하게 거래 기록은 정상 저장된다")
    void requestApprovalAsync_timeoutPrefix_stillPersistsTransaction() {
        // given
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<MockPgTransaction> savedCaptor = ArgumentCaptor.forClass(MockPgTransaction.class);
        given(mockPgTransactionRepository.save(savedCaptor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        mockPaymentGateway.requestApprovalAsync(orderId, "TIMEOUT_anything", 50_000L, PaymentMethod.CARD);

        // then — webhook은 안 갔지만(별도 테스트로 검증됨) 거래 기록은 APPROVED로 저장돼야 한다.
        assertThat(savedCaptor.getValue().getStatus())
                .isEqualTo(PgTransactionResult.APPROVED);
    }

    @Test
    @DisplayName("거래조회 - 저장된 거래가 있으면 진짜 상태를 그대로 반환한다")
    void inquireTransaction_returnsGroundTruthStatus() {
        // given
        UUID orderId = UUID.randomUUID();
        String pgTransactionId = "PG-known";
        MockPgTransaction transaction = MockPgTransaction.approved(pgTransactionId, orderId, 50_000L);
        given(mockPgTransactionRepository.findByPgTransactionId(pgTransactionId))
                .willReturn(Optional.of(transaction));

        // when
        Optional<PgTransactionStatus> result = mockPaymentGateway.inquireTransaction(pgTransactionId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().pgTransactionId()).isEqualTo(pgTransactionId);
        assertThat(result.get().orderId()).isEqualTo(orderId);
        assertThat(result.get().status())
                .isEqualTo(PgTransactionResult.APPROVED);
    }

    @Test
    @DisplayName("거래조회 - 존재하지 않는 거래면 빈 Optional을 반환한다")
    void inquireTransaction_unknownTransaction_returnsEmpty() {
        // given
        given(mockPgTransactionRepository.findByPgTransactionId("PG-unknown")).willReturn(Optional.empty());

        // when
        Optional<PgTransactionStatus> result = mockPaymentGateway.inquireTransaction("PG-unknown");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("비동기 환불 요청 - 이미 REFUND_FAILED인 거래도 재시도 시 정상적으로 REFUNDED로 전이된다(복구 배치 재시도 전제)")
    void requestRefundAsync_retryAfterPriorRefundFailure_transitionsToRefunded() {
        // given — 직전 시도에서 이미 REFUND_FAILED로 끝난 거래(복구 배치가 재시도하는 전형적 케이스)
        UUID orderId = UUID.randomUUID();
        String pgTransactionId = "PG-retry";
        MockPgTransaction alreadyFailed = MockPgTransaction.approved(pgTransactionId, orderId, 50_000L);
        alreadyFailed.markRefundFailed("이전 시도 실패");
        given(mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId))
                .willReturn(Optional.of(alreadyFailed));

        // when — 재시도(이번엔 성공 시나리오의 pgTransactionId이므로 REFUND_FAIL_ 마커가 없음)
        mockPaymentGateway.requestRefundAsync(orderId, pgTransactionId, 50_000L);

        // then — 예외 없이 REFUNDED로 전이되고 콜백이 발송된다
        assertThat(alreadyFailed.getStatus())
                .isEqualTo(PgTransactionResult.REFUNDED);
        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender).sendDelayed(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("비동기 환불 요청 - 기본 pgTransactionId면 REFUNDED 콜백을 예약한다")
    void requestRefundAsync_default_schedulesRefundedCallback() {
        // given — 환불은 이제 PG 자체 거래 기록(승인 시점에 저장됨)이 있어야 처리된다.
        UUID orderId = UUID.randomUUID();
        String pgTransactionId = "PG-1234";
        MockPgTransaction existing = MockPgTransaction.approved(pgTransactionId, orderId, 50_000L);
        given(mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId))
                .willReturn(Optional.of(existing));

        // when
        mockPaymentGateway.requestRefundAsync(orderId, pgTransactionId, 50_000L);

        // then
        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender).sendDelayed(captor.capture());

        assertThat(captor.getValue().status()).isEqualTo("REFUNDED");
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("비동기 환불 요청 - PG 거래 기록이 없으면 콜백을 보내지 않는다(데이터 불일치 방어)")
    void requestRefundAsync_unknownTransaction_neverSendsCallback() {
        // given
        UUID orderId = UUID.randomUUID();
        String pgTransactionId = "PG-unknown";
        given(mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId))
                .willReturn(Optional.empty());

        // when
        mockPaymentGateway.requestRefundAsync(orderId, pgTransactionId, 50_000L);

        // then
        verify(callbackSender, never()).sendDelayed(any());
    }

    @Test
    @DisplayName("비동기 환불 요청 - pgTransactionId가 없으면 콜백을 보내지 않는다")
    void requestRefundAsync_withoutTransactionId_neverSendsCallback() {
        // when
        mockPaymentGateway.requestRefundAsync(UUID.randomUUID(), null, 50_000L);

        // then
        verify(callbackSender, never()).sendDelayed(any());
    }

    @Test
    @DisplayName("승인 시점 idempotencyKey에 REFUND_FAIL_ 마커가 있으면, 그 pgTransactionId로 환불 요청 시 REFUND_FAILED 콜백을 예약한다")
    void requestRefundAsync_refundFailMarker_schedulesRefundFailedCallback() {
        // given
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<MockPgTransaction> savedCaptor = ArgumentCaptor.forClass(MockPgTransaction.class);
        given(mockPgTransactionRepository.save(savedCaptor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(
                orderId, "idem-REFUND_FAIL_xyz", 50_000L, PaymentMethod.CARD);

        // 승인 시점에 저장된 거래 기록을 환불 시점에도 그대로 조회할 수 있어야 한다.
        given(mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId))
                .willReturn(Optional.of(savedCaptor.getValue()));

        // when
        mockPaymentGateway.requestRefundAsync(orderId, pgTransactionId, 50_000L);

        // then
        ArgumentCaptor<PgWebhookRequest> captor = ArgumentCaptor.forClass(PgWebhookRequest.class);
        verify(callbackSender, times(2)).sendDelayed(captor.capture());

        PgWebhookRequest refundCallback = captor.getAllValues().get(1);
        assertThat(refundCallback.status()).isEqualTo("REFUND_FAILED");
        assertThat(refundCallback.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("승인 시점 idempotencyKey에 REFUND_TIMEOUT_ 마커가 있으면, 그 pgTransactionId로 환불 요청 시 콜백을 보내지 않는다")
    void requestRefundAsync_refundTimeoutMarker_neverSendsCallback() {
        // given
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<MockPgTransaction> savedCaptor = ArgumentCaptor.forClass(MockPgTransaction.class);
        given(mockPgTransactionRepository.save(savedCaptor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        String pgTransactionId = mockPaymentGateway.requestApprovalAsync(
                orderId, "idem-REFUND_TIMEOUT_xyz", 50_000L, PaymentMethod.CARD);

        given(mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId))
                .willReturn(Optional.of(savedCaptor.getValue()));

        // when
        mockPaymentGateway.requestRefundAsync(orderId, pgTransactionId, 50_000L);

        // then — 승인 콜백 1번만 발송, 환불 콜백은 없음
        verify(callbackSender, times(1)).sendDelayed(any());
    }
}
