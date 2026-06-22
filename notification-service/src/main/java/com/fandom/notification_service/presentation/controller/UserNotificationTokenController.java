package com.fandom.notification_service.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.notification_service.application.service.UserNotificationTokenService;
import com.fandom.notification_service.presentation.dto.request.SettingUpdateRequest;
import com.fandom.notification_service.presentation.dto.request.TokenRegisterRequest;
import com.fandom.notification_service.presentation.dto.response.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/tokens")
@RequiredArgsConstructor
public class UserNotificationTokenController {

    private final UserNotificationTokenService tokenService;

    // 토큰 등록
    @PostMapping
    public ApiResponse<TokenResponse> register(
            @Valid @RequestBody TokenRegisterRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(
                tokenService.register(idCard.getUserId(), request.deviceToken(), request.deviceType()));
    }

    // 토큰 삭제 - 로그아웃
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @CurrentIdCard UserIdCard idCard
    ) {
        tokenService.delete(idCard.getUserId(), id);
        return ApiResponse.success();
    }

    // 알림 설정 조회
    @GetMapping("/{id}/settings")
    public ApiResponse<TokenResponse> getSetting(
            @PathVariable UUID id,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(tokenService.getSetting(idCard.getUserId(), id));
    }

    // 알림 설정 변경
    @PatchMapping("/{id}/settings")
    public ApiResponse<TokenResponse> updateSetting(
            @PathVariable UUID id,
            @Valid @RequestBody SettingUpdateRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(
                tokenService.updateSetting(idCard.getUserId(), id, request.isNotified()));
    }
}
