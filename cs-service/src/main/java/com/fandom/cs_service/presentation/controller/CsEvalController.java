package com.fandom.cs_service.presentation.controller;

import com.fandom.cs_service.application.service.CsMessageService;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.cs_service.presentation.dto.request.InquiryRequest;
import com.fandom.cs_service.presentation.dto.response.EvalResponse;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// RAGAS 평가 전용
@RestController
@RequestMapping("/api/v1/cs/eval")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class CsEvalController {

    private final CsMessageService csMessageService;

    @PostMapping
    public ApiResponse<EvalResponse> evaluate(
            @Valid @RequestBody InquiryRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        if (!idCard.isMaster()) {
            throw new CustomException(CsErrorCode.CS_ACCESS_DENIED);
        }
        return ApiResponse.success(EvalResponse.from(csMessageService.evaluate(request.question())));
    }
}
