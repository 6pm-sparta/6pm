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

    /** 주문 타임아웃 처자동 리(P1) 기준 시각. 결제 전이라면 이 시각 이후 자동 취소 대상이 된다. */
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

    /** PAYMENT_REQUESTED → FAILED. PG 거절/오류 응답을 받은 직후 호출한다.*/
    public void markFailed() {

        if (this.status != OrderStatus.PAYMENT_REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = OrderStatus.FAILED;
        this.statusUpdatedAt = LocalDateTime.now();
    }
}
