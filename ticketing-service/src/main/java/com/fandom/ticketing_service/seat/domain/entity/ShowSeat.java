package com.fandom.ticketing_service.seat.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Builder
    private ShowSeat(Long showId, String seatName, String grade, int price) {
        this.showId = showId;
        this.seatName = seatName;
        this.grade = grade;
        this.price = price;
    }
}
