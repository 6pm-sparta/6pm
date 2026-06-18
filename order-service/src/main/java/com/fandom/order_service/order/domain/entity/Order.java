package com.fandom.order_service.order.domain.entity;

import com.fandom.common.entity.BaseEntity;

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
}
