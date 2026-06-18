package com.fandom.order_service.order.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private UUID showSeatId;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Builder
    private Order(UUID userId, Long showId, UUID showSeatId, int price) {
        this.userId = userId;
        this.showId = showId;
        this.showSeatId = showSeatId;
        this.price = price;
        this.status = OrderStatus.PENDING;
    }
}
