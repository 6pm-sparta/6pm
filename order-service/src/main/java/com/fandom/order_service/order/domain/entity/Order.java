package com.fandom.order_service.order.domain.entity;

import com.fandom.common.entity.BaseEntity;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문(orders) 엔티티. 주문 상태 머신의 중심 테이블.
 *
 * id(=order_id)는 BaseEntity가 UUIDv7로 생성한다.
 * createdAt/updatedAt도 BaseEntity의 JPA Auditing이 관리한다.
 * statusUpdatedAt은 "마지막 상태 전이 시각"만을 위한 별도 컬럼으로, updatedAt(모든 필드 변경에 반응)과
 * 의미가 다르므로 분리한다
 *
 * 생성 시 status는 항상 PENDING으로 고정한다(요청 바디로 들어온 값은 신뢰하지 않음 — API 명세서 "상태 초기화" 항목).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "userId"),
        @Index(name = "idx_orders_status", columnList = "status")
})
public class Order extends BaseEntity {

    @Column(nullable = false)
    private UUID seatId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private LocalDateTime statusUpdatedAt;

    /**
     * 주문 만료 기준 시각.
     * 결제 타임아웃 처리와 Webhook 미수신으로 인한 PENDING 좀비 상태(결제 요청 중 웹훅 유실) 정리에 사용된다.
     */
    private LocalDateTime expiredAt;

    /**
     * 현재 활성 결제 레코드 포인터.
     * 재시도마다 새 Payment INSERT → O(1)로 최신 결제 조회.
     * Payment INSERT와 같은 트랜잭션에서 갱신. 주문 생성 시점엔 nullable.
     */
    @Column
    private UUID latestPaymentId;

    @Builder
    private Order(UUID seatId, UUID userId, Long totalAmount, LocalDateTime expiredAt) {
        this.seatId = seatId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.expiredAt = expiredAt;
        this.status = OrderStatus.PENDING;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    public static Order createPending(UUID seatId, UUID userId, Long totalAmount, LocalDateTime expiredAt) {
        return Order.builder()
                .seatId(seatId)
                .userId(userId)
                .totalAmount(totalAmount)
                .expiredAt(expiredAt)
                .build();
    }

    /** 결제 시도(신규/재시도) 시 최신 Payment 포인터 갱신. */
    public void updateLatestPayment(UUID paymentId) {
        this.latestPaymentId = paymentId;
    }

    /**
     * PENDING → CONFIRMING. PG 승인 webhook을 받은 직후 호출한다.
     */
    public void markConfirming() {

        if (this.status != OrderStatus.PENDING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CONFIRMING;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PENDING → FAILED. 결제 영구 실패(PG 거절) 또는 결제 재시도 소진 시 호출한다. */
    public void markFailed() {

        if (this.status != OrderStatus.PENDING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.FAILED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PENDING → CANCELLED. 결제 전 취소(유저 직접 또는 타임아웃)에 사용한다. */
    public void markCancelled() {

        if (this.status != OrderStatus.PENDING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CANCELLED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * CONFIRMING/CONFIRMED/FAILED → CANCEL_REQUESTED. PG 환불 호출 "전"에 먼저 반영해야 한다.
     * FAILED → CANCEL_REQUESTED는 환불 복구 배치가 한 번 거절(FAILED)됐던 환불을 재시도할 때 사용한다.
     * 유저 직접 취소(결제후)/취소 가능 시간 내 확정 취소/SAGA 보상(좌석 예매 실패)/복구 배치 재시도가
     * 전부 이 메서드를 거친다. 진입 경로 구분은 상태값이 아니라 order_status_histories.reason
     * 프리픽스([USER]/[SAGA]/[RETRY])로 한다.
     */
    public void markCancelRequested() {

        if (this.status != OrderStatus.CONFIRMING && this.status != OrderStatus.CONFIRMED
                && this.status != OrderStatus.FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CANCEL_REQUESTED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** CONFIRMING → CONFIRMED. ticketing.seat.booked 수신(좌석 예매 확정 완료) 직후 호출한다.*/
    public void markConfirmed() {

        if (this.status != OrderStatus.CONFIRMING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CONFIRMED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * CANCEL_REQUESTED/FAILED → CANCELLED. 환불 완료(PG 웹훅) 또는 복구 배치 거래조회 동기화 시 호출한다.
     */
    public void markCancelCompleted() {

        if (this.status != OrderStatus.CANCEL_REQUESTED && this.status != OrderStatus.FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CANCELLED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** CANCEL_REQUESTED → FAILED. PG가 환불 거절 응답을 반환한 경우 호출한다. */
    public void markCancelFailed() {

        if (this.status != OrderStatus.CANCEL_REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.FAILED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** CANCEL_REQUESTED/FAILED → MANUAL_REVIEW_REQUIRED. 복구 배치 재시도 소진 시 호출. */
    public void markManualReviewRequired() {

        if (this.status != OrderStatus.CANCEL_REQUESTED && this.status != OrderStatus.FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.MANUAL_REVIEW_REQUIRED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * CONFIRMED 상태가 "취소 가능 시간" 내인지 확인한다. 확정 시각(statusUpdatedAt) 기준으로 판단한다.
     * todo : 취소 가능 시간에 대해 협의 해야 함
     */
    public boolean isWithinCancellationWindow(long windowHours) {
        return this.statusUpdatedAt.plusHours(windowHours).isAfter(LocalDateTime.now());
    }
}
