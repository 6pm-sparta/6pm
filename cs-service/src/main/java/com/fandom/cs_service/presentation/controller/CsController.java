package com.fandom.cs_service.presentation.controller;

import com.fandom.cs_service.application.service.CsMessageService;
import com.fandom.cs_service.presentation.dto.request.InquiryRequest;
import com.fandom.cs_service.presentation.dto.response.CsMessageListResponse;
import com.fandom.cs_service.presentation.dto.response.InquiryResponse;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cs")
@RequiredArgsConstructor
public class CsController {

    private final CsMessageService csMessageService;

    // 문의
    @PostMapping("/inquiries")
    public ApiResponse<InquiryResponse> inquire(
            @Valid @RequestBody InquiryRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        String answer = csMessageService.inquire(idCard.getUserId(), request.question());
        return ApiResponse.success(new InquiryResponse(answer));
    }

    // 문의 내역 조회
    @GetMapping("/inquiries")
    public ApiResponse<CsMessageListResponse> getHistory(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) Integer size,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(csMessageService.getHistory(idCard.getUserId(), cursor, size));
    }

    // 문의 내역 초기화
    @DeleteMapping("/inquiries")
    public ApiResponse<Void> clearHistory(@CurrentIdCard UserIdCard idCard) {
        csMessageService.clearHistory(idCard.getUserId());
        return ApiResponse.success();
    }
}
