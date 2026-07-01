package com.fandom.order_service.order.application.refundrecovery;

/**
 * 단건 환불 복구 처리 결과.
 *
 * SYNCED: 거래조회 결과 이미 REFUNDED라 재환불 없이 우리 쪽 상태만 동기화.
 * RETRIED: 거래조회 결과 APPROVED/REFUND_FAILED라 재환불 요청.
 * EXHAUSTED: 재시도 횟수 소진 또는 거래조회 결과 없음 → MANUAL_REVIEW_REQUIRED 전환.
 * SKIPPED: 처리 시점에 이미 다른 경로로 상태가 바뀜(정상 경합, 에러 아님).
 */
public enum RefundRecoveryResult {
    SYNCED,
    RETRIED,
    EXHAUSTED,
    SKIPPED
}
