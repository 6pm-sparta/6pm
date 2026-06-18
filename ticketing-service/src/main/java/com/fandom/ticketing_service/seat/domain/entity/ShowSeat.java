package com.fandom.ticketing_service.seat.domain.entity;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column
    private UUID orderId;

    @Builder
    private ShowSeat(Long showId, String seatName, String grade, int price) {
        this.showId = showId;
        this.seatName = seatName;
        this.grade = grade;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public void hold(UUID orderId) {
        this.status = SeatStatus.HOLDING;
        this.orderId = orderId;
    }

    public void confirm() {
        this.status = SeatStatus.BOOKED;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.orderId = null;
    }
}
