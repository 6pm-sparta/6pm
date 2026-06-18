package com.fandom.ticketing_service.queue.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.ticketing_service.queue.dto.QueueStatusResponse;
import com.fandom.ticketing_service.queue.service.QueueService;
import com.fandom.ticketing_service.queue.service.QueueSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final QueueSseService queueSseService;

    @PostMapping("/shows/{showId}/enter")
    public ResponseEntity<ApiResponse<Void>> enter(
            @PathVariable Long showId,
            @CurrentIdCard UserIdCard idCard
    ) {
        boolean entered = queueService.enter(showId, idCard.getUserId());
        if (!entered) {
            return ResponseEntity.ok(ApiResponse.success(null)); // 이미 대기 중
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/shows/{showId}/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
            @PathVariable Long showId,
            @CurrentIdCard UserIdCard idCard
    ) {
        QueueStatusResponse response = queueService.getStatus(showId, idCard.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/shows/{showId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Long showId,
            @CurrentIdCard UserIdCard idCard
    ) {
        return queueSseService.connect(showId, idCard.getUserId());
    }
}
