package com.fandom.ticketing_service.seat.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.ticketing_service.seat.dto.HoldResponse;
import com.fandom.ticketing_service.seat.dto.ShowSeatResponse;
import com.fandom.ticketing_service.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/shows/{showId}")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/seats")
    public ResponseEntity<ApiResponse<List<ShowSeatResponse>>> getSeats(@PathVariable Long showId) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getSeats(showId)));
    }

    @PostMapping("/seats/{seatId}/hold")
    public ResponseEntity<ApiResponse<HoldResponse>> hold(
            @PathVariable Long showId,
            @PathVariable UUID seatId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ResponseEntity.ok(ApiResponse.success(seatService.hold(seatId, idCard.getUserId())));
    }
}
