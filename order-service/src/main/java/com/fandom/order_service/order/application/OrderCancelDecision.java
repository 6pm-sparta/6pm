package com.fandom.order_service.order.application;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 취소 처리 결과. OrderCancelWriter → OrderCancelService로 전달된다.
 *
 * - CANCELLED: PENDING → CANCELLED 완료. 추가 작업 없음.
 * - IDEMPOTENT: 이미 CANCELLED/REFUNDED인 주문 재요청. 변경 없이 현재 상태 그대로.
 * - REFUND_NEEDED: PAID/CONFIRMED → REFUND_REQUESTED 전이 완료(1단계). 락 밖에서 PG 환불 호출이
 *   필요하며, paymentToRefund에 담긴 pg_transaction_id로 환불을 요청한다.
 * - REFUNDED: PG 환불 성공 후 2단계(applyRefundSuccess) 완료된 최종 결과.
 */
public record OrderCancelDecision(
        Type type,
        UUID orderId,
        OrderStatus status,
        LocalDateTime updatedAt,
        Payment paymentToRefund,
        UUID refundedPaymentId
) {
    public enum Type { CANCELLED, IDEMPOTENT, REFUND_NEEDED, REFUNDED }

    public static OrderCancelDecision cancelled(UUID orderId, OrderStatus status, LocalDateTime updatedAt) {
        return new OrderCancelDecision(Type.CANCELLED, orderId, status, updatedAt, null, null);
    }

    public static OrderCancelDecision idempotent(UUID orderId, OrderStatus status, LocalDateTime updatedAt) {
        return new OrderCancelDecision(Type.IDEMPOTENT, orderId, status, updatedAt, null, null);
    }

    public static OrderCancelDecision refundNeeded(UUID orderId, Payment paymentToRefund) {
        return new OrderCancelDecision(
                Type.REFUND_NEEDED, orderId, OrderStatus.REFUND_REQUESTED, null, paymentToRefund, null);
    }

    public static OrderCancelDecision refunded(UUID orderId, OrderStatus status, UUID paymentId, LocalDateTime updatedAt) {
        return new OrderCancelDecision(Type.REFUNDED, orderId, status, updatedAt, null, paymentId);
    }
}
