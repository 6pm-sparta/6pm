package com.fandom.order_service.order.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 주문 상태 전이 이력(order_status_histories). INSERT만 발생하는 append-only 테이블.
 *
 * orderId는 의도적으로 UUID 평문 필드로만 둔다. Order에 대한 @ManyToOne 연관관계로 만들면
 * 이력을 쌓을 때마다 Order 엔티티를 영속성 컨텍스트에 끌고 와야 하고, 감사 로그 테이블 특성상
 * 다시 객체 그래프를 타고 조회할 일이 거의 없어 연관관계의 이점이 적다. FK 제약은 DB 레벨에서만 보장한다.
 *
 * changed_at은 BaseEntity의 createdAt(JPA Auditing)이 그대로 그 역할을 한다 — 테이블명세서 메모 참고.
 * 별도 컬럼을 추가하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_status_histories")
public class OrderStatusHistory extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    /** 전이 전 상태. 최초 생성(PENDING 진입) 시점에는 이전 상태가 없으므로 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(length = 255)
    private String reason;

    @Builder
    private OrderStatusHistory(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String reason) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
    }

    /** 주문 최초 생성 시(from=null) 이력 레코드. */
    public static OrderStatusHistory initial(UUID orderId, OrderStatus toStatus, String reason) {
        return OrderStatusHistory.builder()
                .orderId(orderId)
                .toStatus(toStatus)
                .reason(reason)
                .build();
    }
}
