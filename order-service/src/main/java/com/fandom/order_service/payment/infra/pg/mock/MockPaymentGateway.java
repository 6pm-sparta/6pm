package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgTransactionStatus;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Mock PG 클라이언트.
 *
 * 실제 PG 없이 로컬/테스트 환경에서 결제 흐름을 검증한다.
 * 시나리오는 랜덤이 아니라 idempotencyKey 규칙으로 결정된다.
 *
 * - FAIL_            : 결제 영구 실패 웹훅(FAILED)
 * - TIMEOUT_         : 결제 웹훅 미발송
 * - TRANSIENT_FAIL_  : 일시적 실패 웹훅(FAILED, failureReason="TRANSIENT:...").
 *                      재시도는 새 idempotencyKey로 오므로 prefix 없어 정상 승인 처리됨.
 * - REFUND_FAIL_     : 승인 후 환불 실패 웹훅(REFUND_FAILED)
 * - REFUND_TIMEOUT_  : 승인 후 환불 웹훅 미발송
 * - 그 외             : 승인(APPROVED), 환불(REFUNDED)
 *
 * 확률적 장애 주입 모드(#236): 위 마커는 기능 검증용 결정론적 시나리오라 부하 테스트에서 매 요청마다
 * 지정할 수 없다. 마커 없는 요청에 한해 MockPgChaosPolicy(ChaosProperties.enabled)가 확률적으로 결과를
 * 흔든다. 판단 자체는 MockPgChaosPolicy에 위임하고, 여기서는 그 결과를 받아 기존 마커 로직과 동일한 흐름
 * (거래 영속화 → webhook 발송)으로 처리한다. 마커가 항상 우선하므로 기존 기능 테스트에는 영향 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_PREFIX = "FAIL_";
    private static final String TIMEOUT_PREFIX = "TIMEOUT_";
    private static final String TRANSIENT_FAIL_PREFIX = "TRANSIENT_FAIL_";
    private static final String TRANSIENT_FAILURE_REASON = "TRANSIENT:PG 일시적 오류";
    private static final String PAYMENT_FAILURE_REASON = "결제 실패";
    private static final String REFUND_FAIL_MARKER = "REFUND_FAIL_";
    private static final String REFUND_TIMEOUT_MARKER = "REFUND_TIMEOUT_";
    private static final String REFUND_FAILURE_REASON = "PG 환불 처리 중 오류가 발생했습니다.";

    private final MockPgWebhookCallbackSender callbackSender;
    private final MockPgTransactionRepository mockPgTransactionRepository;
    private final MockPgChaosPolicy chaosPolicy;

    @Override
    @Transactional
    public String requestApprovalAsync(UUID orderId, String idempotencyKey, Long amount, PaymentMethod paymentMethod) {

        String pgTransactionId = "PG-" + refundScenarioMarker(idempotencyKey) + UUID.randomUUID();

        boolean isMarkedTransientFail = idempotencyKey != null && idempotencyKey.startsWith(TRANSIENT_FAIL_PREFIX);
        boolean isMarkedFail = !isMarkedTransientFail && idempotencyKey != null && idempotencyKey.startsWith(FAIL_PREFIX);
        boolean isMarkedTimeout = !isMarkedTransientFail && !isMarkedFail
                && idempotencyKey != null && idempotencyKey.startsWith(TIMEOUT_PREFIX);
        boolean hasMarker = isMarkedTransientFail || isMarkedFail || isMarkedTimeout;

        MockPgChaosPolicy.Outcome chaos = chaosPolicy.decide(hasMarker);
        ApprovalDecision decision = decideApprovalOutcome(isMarkedTransientFail, isMarkedFail, isMarkedTimeout, chaos);

        // PG는 결과와 무관하게 항상 자신의 거래 기록을 먼저 영속화한다(진짜 상태).
        // webhook 발송 여부(TIMEOUT/LOST 시나리오)는 이 기록과 별개다.
        MockPgTransaction transaction = decision.approved()
                ? MockPgTransaction.approved(pgTransactionId, orderId, amount)
                : MockPgTransaction.failed(pgTransactionId, orderId, amount, decision.failureReason());
        mockPgTransactionRepository.save(transaction);

        if (decision.webhookLost()) {
            log.warn("[MockPG] 비동기 타임아웃/유실 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}, chaos={}",
                    orderId, pgTransactionId, chaos);
            return pgTransactionId;
        }

        String status = decision.approved() ? "APPROVED" : "FAILED";
        PgWebhookRequest payload = new PgWebhookRequest(pgTransactionId, orderId, status, amount, decision.failureReason());

        log.info("[MockPG] 비동기 결제 승인 요청 접수. orderId={}, pgTransactionId={}, paymentMethod={}, chaos={}",
                orderId, pgTransactionId, paymentMethod, chaos);

        dispatchWebhook(payload, chaos);

        return pgTransactionId;
    }

    @Override
    @Transactional
    public void requestRefundAsync(UUID orderId, String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 비동기 환불 요청이 들어왔습니다. 콜백을 보내지 않습니다.");
            return;
        }

        MockPgTransaction transaction = mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId)
                .orElse(null);
        if (transaction == null) {
            // 정상 흐름이라면 결제 승인 시점에 이미 저장돼 있어야 한다. 데이터 불일치 방어용 로그.
            log.error("[MockPG] 거래 기록을 찾을 수 없어 환불 처리를 건너뜁니다. pgTransactionId={}, orderId={}",
                    pgTransactionId, orderId);
            return;
        }

        boolean isMarkedFail = pgTransactionId.contains(REFUND_FAIL_MARKER);
        boolean isMarkedTimeout = pgTransactionId.contains(REFUND_TIMEOUT_MARKER);
        boolean hasMarker = isMarkedFail || isMarkedTimeout;

        MockPgChaosPolicy.Outcome chaos = chaosPolicy.decide(hasMarker);
        RefundDecision decision = decideRefundOutcome(isMarkedFail, isMarkedTimeout, chaos);

        // 거래 상태를 먼저 갱신한다(진짜 상태). webhook 발송 여부와 무관하다.
        if (decision.failed()) {
            transaction.markRefundFailed(REFUND_FAILURE_REASON);
        } else {
            transaction.markRefunded();
        }

        if (decision.webhookLost()) {
            log.warn("[MockPG] 환불 비동기 타임아웃/유실 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}, chaos={}",
                    orderId, pgTransactionId, chaos);
            return;
        }

        PgWebhookRequest payload = decision.failed()
                ? new PgWebhookRequest(pgTransactionId, orderId, "REFUND_FAILED", amount, REFUND_FAILURE_REASON)
                : new PgWebhookRequest(pgTransactionId, orderId, "REFUNDED", amount, null);

        log.info("[MockPG] 비동기 환불 요청 접수. orderId={}, pgTransactionId={}, amount={}, chaos={}",
                orderId, pgTransactionId, amount, chaos);

        dispatchWebhook(payload, chaos);
    }

    @Override
    public Optional<PgTransactionStatus> inquireTransaction(String pgTransactionId) {

        return mockPgTransactionRepository.findByPgTransactionId(pgTransactionId)
                .map(t -> new PgTransactionStatus(
                        t.getPgTransactionId(), t.getOrderId(), t.getAmount(), t.getStatus(), t.getFailureReason()));
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

    /** 마커 + chaos 판정 결과를 "승인 여부 / webhook 발송 여부 / 실패 사유" 하나로 정리한다. */
    private ApprovalDecision decideApprovalOutcome(boolean isMarkedTransientFail, boolean isMarkedFail,
                                                   boolean isMarkedTimeout, MockPgChaosPolicy.Outcome chaos) {
        if (isMarkedTransientFail) {
            return new ApprovalDecision(false, false, TRANSIENT_FAILURE_REASON);
        }
        if (isMarkedFail || chaos == MockPgChaosPolicy.Outcome.FAILED) {
            return new ApprovalDecision(false, false, PAYMENT_FAILURE_REASON);
        }
        boolean webhookLost = isMarkedTimeout || chaos == MockPgChaosPolicy.Outcome.LOST;
        return new ApprovalDecision(true, webhookLost, null);
    }

    /** 마커 + chaos 판정 결과를 "환불 실패 여부 / webhook 발송 여부" 하나로 정리한다. */
    private RefundDecision decideRefundOutcome(boolean isMarkedFail, boolean isMarkedTimeout, MockPgChaosPolicy.Outcome chaos) {
        boolean failed = isMarkedFail || chaos == MockPgChaosPolicy.Outcome.FAILED;
        boolean webhookLost = isMarkedTimeout || chaos == MockPgChaosPolicy.Outcome.LOST;
        return new RefundDecision(failed, webhookLost);
    }

    /** SLOW면 지연 지터를 추가해서 보내고, 아니면 평소대로 보낸다. */
    private void dispatchWebhook(PgWebhookRequest payload, MockPgChaosPolicy.Outcome chaos) {
        if (chaos == MockPgChaosPolicy.Outcome.SLOW) {
            callbackSender.sendDelayed(payload, chaosPolicy.randomJitterMillis());
        } else {
            callbackSender.sendDelayed(payload);
        }
    }

    private record ApprovalDecision(boolean approved, boolean webhookLost, String failureReason) {
    }

    private record RefundDecision(boolean failed, boolean webhookLost) {
    }
}
