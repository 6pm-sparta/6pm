package com.fandom.order_service.order.domain.entity;

import java.util.Set;

/**
 * 주문 상태 머신. order/payment 설계 문서 "3. 주문 상태 머신" 기준.
 *
 * - 정상 흐름: PENDING → PAYMENT_REQUESTED → PAID → CONFIRMED
 * - 결제 실패: PAYMENT_REQUESTED → FAILED
 * - 유저 직접 취소(결제 전): PENDING → CANCELLED
 * - 주문 타임아웃 만료: PENDING → CANCELLED
 * - 유저 직접 취소(결제 후): PAID → REFUND_REQUESTED → REFUNDED
 * - 예매 확정 후 취소(취소 가능 시간 내): CONFIRMED → REFUND_REQUESTED → REFUNDED
 * - SAGA 보상 트랜잭션: PAID → COMPENSATING → REFUND_REQUESTED → REFUNDED
 * - 환불 거절(PG webhook): REFUND_REQUESTED → FAILED
 * - 환불 복구 배치 재시도 소진: REFUND_REQUESTED/FAILED → MANUAL_REVIEW_REQUIRED (issue #96)
 *
 * CONFIRMED는 취소 가능 기간 내 환불로 전이될 수 있으며,
 * CANCELLED, REFUNDED, FAILED, MANUAL_REVIEW_REQUIRED를 종료 상태로 본다.
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_REQUESTED,
    PAID,
    CONFIRMED,
    COMPENSATING,
    REFUND_REQUESTED,
    CANCELLED,
    REFUNDED,
    FAILED,
    MANUAL_REVIEW_REQUIRED;

    /**
     * 동일 seatId에 대해 "진행중"으로 간주하는 상태 집합.
     * orders 테이블의 부분 UNIQUE 인덱스(uq_orders_seat_active)와 정확히 동일한 조건을 유지해야 한다.
     * 이 조건이 어긋나면 인덱스가 막는 범위와 애플리케이션이 막는 범위가 달라져 멱등성이 깨진다.
     */
    public static final Set<OrderStatus> ACTIVE = Set.of(PENDING, PAYMENT_REQUESTED, PAID);
}
