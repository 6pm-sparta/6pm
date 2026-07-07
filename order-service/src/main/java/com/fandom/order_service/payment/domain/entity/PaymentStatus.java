package com.fandom.order_service.payment.domain.entity;

/**
 * 결제 시도 상태값.
 *
 * - PENDING: 결제 요청 전 대기 (레코드 생성 시점, 현재 흐름에서는 거의 즉시 REQUESTED로 넘어간다)
 * - REQUESTED: PG사 결제 API 호출 완료, 승인 대기 중
 * - APPROVED: PG사 결제 승인 완료
 * - FAILED: 결제 실패
 * - CANCELLED: 결제 취소
 * - REFUND_REQUESTED: PG사 환불 API 호출 완료, 환불 처리 대기 중
 * - REFUNDED: 환불 완료
 * - REFUND_FAILED: PG사 환불 거절/오류
 */
public enum PaymentStatus {
    PENDING,
    REQUESTED,
    APPROVED,
    FAILED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED,
    REFUND_FAILED
}
