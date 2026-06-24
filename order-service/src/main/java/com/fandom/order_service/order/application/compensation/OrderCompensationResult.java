package com.fandom.order_service.order.application.compensation;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;

import java.util.UUID;

/**
 * SAGA 보상 트랜잭션 1단계(startCompensation) 처리 결과. OrderCompensationWriter → OrderCompensationService로
 * 전달된다.
 *
 * - REFUND_REQUESTED_STARTED: PAID/CONFIRMED → COMPENSATING → REFUND_REQUESTED 전이 완료(한 트랜잭션
 *   안에서 두 단계 모두 처리됨, #110). 락 밖에서 비동기 PG 환불 요청이 필요하며, paymentToRefund/userId에
 *   담긴 값으로 진행한다. 실제 환불 완료/거절 결과는 PG 웹훅으로 비동기 반영되므로(RefundResultWriter
 *   참고) 이 Writer는 더 이상 최종 결과(REFUNDED/FAILED)를 직접 만들지 않는다.
 * - ALREADY_HANDLED: 변경 없이 멱등 처리.
 * - SKIPPED_INVALID_STATE: 보상 대상이 아님.
 */
public record OrderCompensationResult(
        Type type,
        UUID orderId,
        OrderStatus status,
        Payment paymentToRefund,
        UUID userId
) {
    public enum Type { REFUND_REQUESTED_STARTED, ALREADY_HANDLED, SKIPPED_INVALID_STATE }

    public static OrderCompensationResult refundRequestedStarted(UUID orderId, Payment paymentToRefund, UUID userId) {
        return new OrderCompensationResult(
                Type.REFUND_REQUESTED_STARTED, orderId, OrderStatus.REFUND_REQUESTED, paymentToRefund, userId);
    }

    public static OrderCompensationResult alreadyHandled(UUID orderId, OrderStatus status) {
        return new OrderCompensationResult(Type.ALREADY_HANDLED, orderId, status, null, null);
    }

    public static OrderCompensationResult skippedInvalidState(UUID orderId, OrderStatus status) {
        return new OrderCompensationResult(Type.SKIPPED_INVALID_STATE, orderId, status, null, null);
    }
}