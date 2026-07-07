package com.fandom.order_service.order.application.confirmation;

import java.util.UUID;

/**
 * ticketing.seat.booked 수신 처리 결과. OrderConfirmationWriter → OrderConfirmationService로 전달된다.
 *
 * - CONFIRMED: CONFIRMING → CONFIRMED 전이 완료. notification.send(ORDER_COMPLETED) 발행 대상.
 * - ALREADY_CONFIRMED: Kafka at-least-once 재전송 등으로 동일 이벤트가 중복 수신된 경우.
 *   변경 없이 멱등 처리하고 이벤트는 재발행하지 않는다.
 * - SKIPPED_INVALID_STATE: CONFIRMING이 아닌 상태(예: 그 사이 유저가 취소해 CANCEL_REQUESTED로 빠진
 *   경우)에서 늦게 도착한 이벤트. 예외를 던지지 않고 로그만 남기고 스킵한다 — 이미 다른 경로로 종료된
 *   주문에 대해 Kafka 재시도를 유발할 이유가 없다.
 */
public record OrderConfirmationResult(Type type, UUID orderId, UUID userId) {

    public enum Type { CONFIRMED, ALREADY_CONFIRMED, SKIPPED_INVALID_STATE }

    public static OrderConfirmationResult confirmed(UUID orderId, UUID userId) {
        return new OrderConfirmationResult(Type.CONFIRMED, orderId, userId);
    }

    public static OrderConfirmationResult alreadyConfirmed(UUID orderId) {
        return new OrderConfirmationResult(Type.ALREADY_CONFIRMED, orderId, null);
    }

    public static OrderConfirmationResult skippedInvalidState(UUID orderId) {
        return new OrderConfirmationResult(Type.SKIPPED_INVALID_STATE, orderId, null);
    }
}
