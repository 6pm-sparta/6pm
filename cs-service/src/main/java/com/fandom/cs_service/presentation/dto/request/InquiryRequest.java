package com.fandom.cs_service.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InquiryRequest(
        @NotBlank(message = "문의 내용을 입력해주세요.")
        String question
) {
}
