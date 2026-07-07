package com.fandom.order_service.order.application.cancellation;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 취소 처리 결과. OrderCancelWriter → OrderCancelService로 전달된다.
 *
 * - CANCELLED: PENDING → CANCELLED 완료. 추가 작업 없음.
 * - IDEMPOTENT: 이미 종료 상태(CANCELLED 등)인 주문 재요청. 변경 없이 현재 상태 그대로.
 * - REFUND_NEEDED: CONFIRMING/CONFIRMED → CANCEL_REQUESTED 전이 완료. 락 밖에서 비동기 PG 환불 요청이
 *   필요하며, paymentToRefund에 담긴 pg_transaction_id로 환불을 요청한다. 실제 환불 완료/거절 결과는
 *   PG 웹훅으로 비동기 반영되므로, 이 결과를 받은 직후 응답은 CANCEL_REQUESTED 상태로 나간다.
 */
public record OrderCancelDecision(
        Type type,
        UUID orderId,
        OrderStatus status,
        LocalDateTime updatedAt,
        Payment paymentToRefund
) {
    public enum Type { CANCELLED, IDEMPOTENT, REFUND_NEEDED }

    public static OrderCancelDecision cancelled(UUID orderId, OrderStatus status, LocalDateTime updatedAt) {
        return new OrderCancelDecision(Type.CANCELLED, orderId, status, updatedAt, null);
    }

    public static OrderCancelDecision idempotent(UUID orderId, OrderStatus status, LocalDateTime updatedAt) {
        return new OrderCancelDecision(Type.IDEMPOTENT, orderId, status, updatedAt, null);
    }

    public static OrderCancelDecision refundNeeded(UUID orderId, Payment paymentToRefund, LocalDateTime updatedAt) {
        return new OrderCancelDecision(
                Type.REFUND_NEEDED, orderId, OrderStatus.CANCEL_REQUESTED, updatedAt, paymentToRefund);
    }
}
