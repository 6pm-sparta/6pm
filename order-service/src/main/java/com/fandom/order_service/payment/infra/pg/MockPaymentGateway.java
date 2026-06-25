package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock PG 클라이언트.
 *
 * 실제 PG 없이 로컬/테스트 환경에서 결제 흐름을 검증한다.
 * 시나리오는 랜덤이 아니라 idempotencyKey 규칙으로 결정된다.
 *
 * - FAIL_            : 결제 실패 웹훅(FAILED)
 * - TIMEOUT_         : 결제 웹훅 미발송
 * - REFUND_FAIL_     : 승인 후 환불 실패 웹훅(REFUND_FAILED)
 * - REFUND_TIMEOUT_  : 승인 후 환불 웹훅 미발송
 * - 그 외             : 승인(APPROVED), 환불(REFUNDED)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_PREFIX = "FAIL_";
    private static final String TIMEOUT_PREFIX = "TIMEOUT_";
    private static final String REFUND_FAIL_MARKER = "REFUND_FAIL_";
    private static final String REFUND_TIMEOUT_MARKER = "REFUND_TIMEOUT_";

    private final PgWebhookCallbackSender callbackSender;

    @Override
    public String requestApprovalAsync(UUID orderId, String idempotencyKey, Long amount, PaymentMethod paymentMethod) {

        String pgTransactionId = "PG-" + refundScenarioMarker(idempotencyKey) + UUID.randomUUID();

        if (idempotencyKey != null && idempotencyKey.startsWith(TIMEOUT_PREFIX)) {

            // PG가 결과 웹훅을 보내지 않는 상황을 시뮬레이션한다.
            log.warn("[MockPG] 비동기 타임아웃 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}",
                    orderId, pgTransactionId);
            return pgTransactionId;
        }

        boolean willFail = idempotencyKey != null && idempotencyKey.startsWith(FAIL_PREFIX);
        PgWebhookRequest payload = willFail
                ? new PgWebhookRequest(pgTransactionId, orderId, "FAILED", amount, "잔액이 부족합니다.")
                : new PgWebhookRequest(pgTransactionId, orderId, "APPROVED", amount, null);

        log.info("[MockPG] 비동기 결제 승인 요청 접수. orderId={}, pgTransactionId={}, paymentMethod={}",
                orderId, pgTransactionId, paymentMethod);
        callbackSender.sendDelayed(payload);

        return pgTransactionId;
    }

    @Override
    public void requestRefundAsync(UUID orderId, String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 비동기 환불 요청이 들어왔습니다. 콜백을 보내지 않습니다.");
            return;
        }

        if (pgTransactionId.contains(REFUND_TIMEOUT_MARKER)) {

            // 환불 결과 웹훅이 오지 않는 상황을 시뮬레이션한다.
            log.warn("[MockPG] 환불 비동기 타임아웃 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}",
                    orderId, pgTransactionId);
            return;
        }

        boolean willFail = pgTransactionId.contains(REFUND_FAIL_MARKER);
        PgWebhookRequest payload = willFail
                ? new PgWebhookRequest(pgTransactionId, orderId, "REFUND_FAILED", amount, "PG 환불 처리 중 오류가 발생했습니다.")
                : new PgWebhookRequest(pgTransactionId, orderId, "REFUNDED", amount, null);

        log.info("[MockPG] 비동기 환불 요청 접수. orderId={}, pgTransactionId={}, amount={}",
                orderId, pgTransactionId, amount);
        callbackSender.sendDelayed(payload);
    }

    /**
     * 환불 시나리오 재현을 위해 idempotencyKey의 마커를
     * pgTransactionId에 포함시킨다.
     *
     * 환불 요청 시에는 pgTransactionId만 전달되므로
     * 승인 시점에 마커를 미리 저장해둔다.
     */
    private String refundScenarioMarker(String idempotencyKey) {
        if (idempotencyKey == null) {
            return "";
        }
        if (idempotencyKey.contains(REFUND_FAIL_MARKER)) {
            return REFUND_FAIL_MARKER;
        }
        if (idempotencyKey.contains(REFUND_TIMEOUT_MARKER)) {
            return REFUND_TIMEOUT_MARKER;
        }
        return "";
    }
}
