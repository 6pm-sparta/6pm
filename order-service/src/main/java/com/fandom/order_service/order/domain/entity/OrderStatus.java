package com.fandom.order_service.order.domain.entity;

import java.util.Set;

/**
 * 주문 상태 머신.
 *
 * orders.status는 주문 자체의 비즈니스 상태만 표현한다. 결제/환불의 진행 상태는
 * payments.payment_status가 담당한다(REQUESTED, REFUND_REQUESTED 등).
 *
 * - 정상 흐름: PENDING → CONFIRMING → CONFIRMED
 * - 결제 영구 실패 / 재시도 소진: PENDING → FAILED
 * - 유저 직접 취소(결제 전) / 타임아웃: PENDING → CANCELLED
 * - 유저 직접 취소(결제 후): CONFIRMING → CANCEL_REQUESTED → CANCELLED
 * - 예매 확정 후 취소(취소 가능 시간 내): CONFIRMED → CANCEL_REQUESTED → CANCELLED
 * - SAGA 보상 트랜잭션(좌석 예매 실패): CONFIRMING/CONFIRMED → CANCEL_REQUESTED → CANCELLED
 * - 환불 거절(PG webhook): CANCEL_REQUESTED → FAILED
 * - 환불 복구 배치 재시도 소진: CANCEL_REQUESTED/FAILED → MANUAL_REVIEW_REQUIRED (issue #96)
 *
 * CANCEL_REQUESTED는 유저 취소/SAGA 보상/복구 배치 재시도가 공통으로 거치는 진입점이며,
 * 되돌릴 수 없는 지점(exit point)이다. 진입 경로 구분은 상태값이 아니라
 * order_status_histories.reason 프리픽스([USER]/[SAGA]/[RETRY])로 한다.
 *
 * CANCELLED, FAILED, MANUAL_REVIEW_REQUIRED를 종료 상태로 본다.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMING,
    CONFIRMED,
    CANCEL_REQUESTED,
    CANCELLED,
    FAILED,
    MANUAL_REVIEW_REQUIRED;

    /**
     * 동일 seatId에 대해 "진행중"으로 간주하는 상태 집합.
     * orders 테이블의 부분 UNIQUE 인덱스(uq_orders_seat_active)와 정확히 동일한 조건을 유지해야 한다.
     * 이 조건이 어긋나면 인덱스가 막는 범위와 애플리케이션이 막는 범위가 달라져 멱등성이 깨진다.
     */
    public static final Set<OrderStatus> ACTIVE = Set.of(PENDING, CONFIRMING);
}
