package com.fandom.ticketing_service.domain;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "venue_seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String seatNumber;

    @Builder
    private VenueSeat(Venue venue, String section, String seatNumber) {
        this.venue = venue;
        this.section = section;
        this.seatNumber = seatNumber;
    }
}
