package com.fandom.order_service.payment.infra.pg;

/**
 * Mock PG 결제 승인 결과. 실제 PG 연동 시에도 이 인터페이스(PaymentGateway)와 결과 모델은
 * 그대로 유지하고 구현체만 교체하는 것을 목표로 한다(DIP).
 *
 * status별 의미:
 * - APPROVED: 승인 성공. pgTransactionId가 채워진다.
 * - DECLINED: PG가 명시적으로 거절 응답을 준 경우(한도초과, 잔액부족 등). failureReason이 채워진다.
 *   API 명세서 502 PG_ERROR에 대응.
 * - TIMEOUT: 응답 자체를 받지 못한 경우. 실제 승인 여부가 불확실하므로 APPROVED/DECLINED와는 다르게 취급해야 한다.
 *   MVP는 Webhook이 없어 동기 흐름 안에서 최종 처리해야 하므로, 호출 측(PaymentRequestService)에서
 *   TIMEOUT을 어떻게 다룰지는 별도 결정 필요 — 현재는 DECLINED와 동일하게 502로 처리하는 것을 임시값으로 둔다.
 */
public record PgApprovalResult(
        PgResultStatus status,
        String pgTransactionId,
        String failureReason
) {
    public enum PgResultStatus {
        APPROVED,
        DECLINED,
        TIMEOUT
    }

    public static PgApprovalResult approved(String pgTransactionId) {
        return new PgApprovalResult(PgResultStatus.APPROVED, pgTransactionId, null);
    }

    public static PgApprovalResult declined(String failureReason) {
        return new PgApprovalResult(PgResultStatus.DECLINED, null, failureReason);
    }

    public static PgApprovalResult timeout() {
        return new PgApprovalResult(PgResultStatus.TIMEOUT, null, null);
    }

    public boolean isApproved() {
        return status == PgResultStatus.APPROVED;
    }
}
