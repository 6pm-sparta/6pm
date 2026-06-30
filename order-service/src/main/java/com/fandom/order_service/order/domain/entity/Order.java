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
     * 결제 타임아웃 처리와 Webhook 미수신으로 인한 PAYMENT_REQUESTED 좀비 상태 정리에 사용된다.
     */
    private LocalDateTime expiredAt;

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

    /**
     * PENDING → PAYMENT_REQUESTED. PG 호출 "전"에 먼저 반영해야 한다.
     * 동시 요청 중 두 번째가 이 전이 이후 들어오면 status가 이미 PAYMENT_REQUESTED라 즉시 거부된다.
     *
     * @throws CustomException 호출 시점에 PENDING이 아니면 발생. 이 메서드는 항상 비관적 락으로
     *         조회한 Order에 대해, 서비스 레이어(PaymentRequestWriter)가 PENDING 여부를 먼저 명시적으로
     *         확인(PaymentErrorCode.INVALID_ORDER_STATUS로 409 응답)한 뒤 호출하는 것을 전제로 한다.
     *         여기서 또 던지는 예외는 그 가드를 건너뛰고 직접 호출하는 실수를 막기 위한 방어적 체크라
     *         정상 흐름에서는 발생하지 않으며, CommonErrorCode.INTERNAL_SERVER_ERROR(500)로 처리한다.
     */
    public void markPaymentRequested() {

        if (this.status != OrderStatus.PENDING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.PAYMENT_REQUESTED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PAYMENT_REQUESTED → PAID. PG 승인 응답을 받은 직후 호출한다.*/
    public void markPaid() {

        if (this.status != OrderStatus.PAYMENT_REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.PAID;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PAYMENT_REQUESTED → FAILED. PG 거절/오류 응답을 받은 직후 호출한다.
     *  COMPENSATING → FAILED. SAGA 보상(환불 재시도) 최종 실패 시에도 사용한다. */
    public void markFailed() {

        if (this.status != OrderStatus.PAYMENT_REQUESTED && this.status != OrderStatus.COMPENSATING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.FAILED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PENDING → CANCELLED. 결제 전 취소(유저 직접 또는 추후 타임아웃 P1)에 사용한다. */
    public void markCancelled() {

        if (this.status != OrderStatus.PENDING) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CANCELLED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * PAID/CONFIRMED/COMPENSATING/FAILED → REFUND_REQUESTED. PG 환불 호출 "전"에 먼저 반영해야 한다.
     * COMPENSATING → REFUND_REQUESTED도 허용한다 — SAGA 보상 트랜잭션이 COMPENSATING을 거쳐
     * 같은 REFUND_REQUESTED 상태로 들어오기 때문.
     * FAILED → REFUND_REQUESTED도 허용한다 — 환불 복구 배치가 한 번 거절(FAILED)됐던 환불을
     * 재시도할 때 사용. REFUND_REQUESTED 자체는 "PG 환불 호출 중/완료 대기"라는 의미만 가지며,
     * 거기 들어온 경로(유저 직접 취소/SAGA 보상/복구 배치 재시도)는 상태값이 아니라
     * order_status_histories.reason으로 구분한다.
     */
    public void markRefundRequested() {

        if (this.status != OrderStatus.PAID && this.status != OrderStatus.CONFIRMED
                && this.status != OrderStatus.COMPENSATING && this.status != OrderStatus.FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.REFUND_REQUESTED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** REFUND_REQUESTED/FAILED → REFUNDED. FAILED 허용은 복구 배치의 거래조회 동기화용. */
    public void markRefunded() {

        if (this.status != OrderStatus.REFUND_REQUESTED && this.status != OrderStatus.FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.REFUNDED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * PAID/CONFIRMED → COMPENSATING. ticketing.seat.book.failed 수신(좌석 예매 확정 실패) 직후
     * 호출한다(SAGA 보상 트랜잭션의 시작점).
     */
    public void markCompensating() {

        if (this.status != OrderStatus.PAID && this.status != OrderStatus.CONFIRMED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.COMPENSATING;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** PAID → CONFIRMED. ticketing.seat.booked 수신(좌석 예매 확정 완료) 직후 호출한다.*/
    public void markConfirmed() {

        if (this.status != OrderStatus.PAID) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.CONFIRMED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /**
     * REFUND_REQUESTED → FAILED.
     * PG가 환불 거절 응답(REFUND_FAILED)을 반환한 경우 호출한다.
     */
    public void markRefundFailed() {

        if (this.status != OrderStatus.REFUND_REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.FAILED;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    /** REFUND_REQUESTED/FAILED → MANUAL_REVIEW_REQUIRED. 복구 배치 재시도 소진 시 호출. */
    public void markManualReviewRequired() {

        if (this.status != OrderStatus.REFUND_REQUESTED && this.status != OrderStatus.FAILED) {
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
