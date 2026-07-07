package com.fandom.notification_service.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.notification_service.application.service.NotificationInboxService;
import com.fandom.notification_service.presentation.dto.response.InboxResponse;
import com.fandom.notification_service.presentation.dto.response.NotificationItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService inboxService;

    // 보관함 조회
    @GetMapping
    public ApiResponse<InboxResponse> getInbox(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(inboxService.getInbox(idCard.getUserId(), cursor, size));
    }

    // 읽음 처리
    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationItemResponse> markRead(
            @PathVariable UUID id,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(inboxService.markRead(idCard.getUserId(), id));
    }

    // 전체 비우기
    @DeleteMapping
    public ApiResponse<Void> clearAll(@CurrentIdCard UserIdCard idCard) {
        inboxService.clearAll(idCard.getUserId());
        return ApiResponse.success();
    }
}
