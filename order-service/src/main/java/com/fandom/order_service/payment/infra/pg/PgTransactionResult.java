package com.fandom.order_service.payment.infra.pg;

/**
 * PG 거래의 진짜 처리 결과(PG사 입장에서 본 결과). order-service의 결제 상태와는 별개 축이다.
 *
 * APPROVED: 승인 완료 / FAILED: 승인 거절 / REFUNDED: 환불 완료 / REFUND_FAILED: 환불 거절
 */
public enum PgTransactionResult {
    APPROVED,
    FAILED,
    REFUNDED,
    REFUND_FAILED
}
