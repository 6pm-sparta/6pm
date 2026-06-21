package com.fandom.order_service.payment.infra.pg;

/**
 * Mock PG 환불 결과. 결제 취소/환불(다음 이슈)에서 사용한다.
 */
public record PgRefundResult(
        boolean isSuccess,
        String failureReason
) {
    public static PgRefundResult success() {
        return new PgRefundResult(true, null);
    }

    public static PgRefundResult failure(String reason) {
        return new PgRefundResult(false, reason);
    }
}