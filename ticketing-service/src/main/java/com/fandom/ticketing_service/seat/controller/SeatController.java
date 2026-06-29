package com.fandom.ticketing_service.seat.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.ticketing_service.seat.dto.HoldResponse;
import com.fandom.ticketing_service.seat.dto.PurchaseLimitResponse;
import com.fandom.ticketing_service.seat.dto.ShowSeatResponse;
import com.fandom.ticketing_service.seat.service.SeatService;
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
    public ResponseEntity<ApiResponse<HoldResponse>> hold(
            @PathVariable UUID showId,
            @PathVariable UUID seatId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ResponseEntity.ok(ApiResponse.success(seatService.hold(seatId, idCard.getUserId())));
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

    // TODO: api 엔드포인트 설계 괜찮은지 검토 필요
    @GetMapping("/purchase-limit")
    public ResponseEntity<ApiResponse<PurchaseLimitResponse>> getPurchaseLimit(
            @PathVariable UUID showId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getPurchaseLimit(showId, idCard.getUserId())));
    }
}
