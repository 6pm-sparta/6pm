package com.fandom.order_service.order.application.compensation;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SAGA 보상 트랜잭션 처리 결과. OrderCompensationWriter → OrderCompensationService로 전달된다.
 *
 * - COMPENSATING_STARTED: PAID/CONFIRMED → COMPENSATING 전이 완료(1단계). 락 밖에서 PG 환불
 *   재시도가 필요하며, paymentToRefund/userId에 담긴 값으로 진행한다.
 * - ALREADY_HANDLED: 변경 없이 멱등 처리.
 * - SKIPPED_INVALID_STATE: 보상 대상이 아님.
 * - REFUNDED: PG 환불 성공 후 2단계(applyRefundSuccess) 완료된 최종 결과.
 * - FAILED: 재시도 소진 후 3단계(applyRefundFailure) 완료된 최종 결과. 수동 처리 대상.
 */
public record OrderCompensationResult(
        Type type,
        UUID orderId,
        OrderStatus status,
        LocalDateTime updatedAt,
        Payment paymentToRefund,
        UUID userId
) {
    public enum Type { COMPENSATING_STARTED, ALREADY_HANDLED, SKIPPED_INVALID_STATE, REFUNDED, FAILED }

    public static OrderCompensationResult compensatingStarted(UUID orderId, Payment paymentToRefund, UUID userId) {
        return new OrderCompensationResult(
                Type.COMPENSATING_STARTED, orderId, OrderStatus.COMPENSATING, null, paymentToRefund, userId);
    }

    public static OrderCompensationResult alreadyHandled(UUID orderId, OrderStatus status) {
        return new OrderCompensationResult(Type.ALREADY_HANDLED, orderId, status, null, null, null);
    }

    public static OrderCompensationResult skippedInvalidState(UUID orderId, OrderStatus status) {
        return new OrderCompensationResult(Type.SKIPPED_INVALID_STATE, orderId, status, null, null, null);
    }

    public static OrderCompensationResult refunded(UUID orderId, LocalDateTime updatedAt) {
        return new OrderCompensationResult(Type.REFUNDED, orderId, OrderStatus.REFUNDED, updatedAt, null, null);
    }

    public static OrderCompensationResult failed(UUID orderId, LocalDateTime updatedAt) {
        return new OrderCompensationResult(Type.FAILED, orderId, OrderStatus.FAILED, updatedAt, null, null);
    }
}
