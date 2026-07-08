package com.fandom.ticketing_service.seat.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.ticketing_service.seat.presentation.dto.HoldResponse;
import com.fandom.ticketing_service.seat.presentation.dto.PurchaseLimitResponse;
import com.fandom.ticketing_service.seat.presentation.dto.ShowSeatResponse;
import com.fandom.ticketing_service.seat.application.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/shows/{showId}")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/seats")
    public ResponseEntity<ApiResponse<List<ShowSeatResponse>>> getSeats(@PathVariable UUID showId) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getSeats(showId)));
    }

    @PostMapping("/seats/{seatId}/hold")
    public ResponseEntity<ApiResponse<Void>> hold(
            @PathVariable UUID showId,
            @PathVariable UUID seatId,
            @CurrentIdCard UserIdCard idCard
    ) {
        seatService.hold(seatId, idCard.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/seats/{seatId}/checkout")
    public ResponseEntity<ApiResponse<HoldResponse>> checkout(
            @PathVariable UUID showId,
            @PathVariable UUID seatId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ResponseEntity.ok(ApiResponse.success(seatService.checkout(seatId, idCard.getUserId())));
    }

    @DeleteMapping("/seats/{seatId}/hold")
    public ResponseEntity<ApiResponse<Void>> releaseHold(
            @PathVariable UUID showId,
            @PathVariable UUID seatId,
            @CurrentIdCard UserIdCard idCard
    ) {
        seatService.releaseHold(seatId, idCard.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/purchase-limit")
    public ResponseEntity<ApiResponse<PurchaseLimitResponse>> getPurchaseLimit(
            @PathVariable UUID showId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getPurchaseLimit(showId, idCard.getUserId())));
    }
}
