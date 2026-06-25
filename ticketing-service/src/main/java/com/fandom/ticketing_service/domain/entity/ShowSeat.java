package com.fandom.ticketing_service.domain;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "show_seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowSeat extends BaseEntity {

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false, length = 20)
    private String seatName;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(nullable = false)
    private int price;

    @Column
    private UUID orderId;

    @Builder
    private ShowSeat(Long showId, String seatName, String grade, int price) {
        this.showId = showId;
        this.seatName = seatName;
        this.grade = grade;
        this.price = price;
    }

    public void assignOrder(UUID orderId) {
        this.orderId = orderId;
    }

    // 좌석은 재사용 자원이라 row를 soft delete하지 않고 orderId만 비운다
    public void releaseOrder() {
        this.orderId = null;
    }
}
