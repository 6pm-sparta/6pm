package com.fandom.ticketing_service.show.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "performances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String title;

    @Builder
    private Performance(Venue venue, String title) {
        this.venue = venue;
        this.title = title;
    }
}
