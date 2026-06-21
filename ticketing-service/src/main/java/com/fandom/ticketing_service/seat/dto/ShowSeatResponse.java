package com.fandom.ticketing_service.seat.dto;

import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;

import java.util.UUID;

public record ShowSeatResponse(
        UUID seatId,
        String seatName,
        String grade,
        int price,
        String status
) {
    public static ShowSeatResponse of(ShowSeat seat, String status) {
        return new ShowSeatResponse(seat.getId(), seat.getSeatName(), seat.getGrade(), seat.getPrice(), status);
    }
}
