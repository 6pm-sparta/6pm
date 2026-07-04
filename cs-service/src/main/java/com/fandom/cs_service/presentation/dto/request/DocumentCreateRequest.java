package com.fandom.cs_service.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentCreateRequest(
        @NotBlank(message = "문서 제목을 입력해주세요.")
        String title,
        @NotBlank(message = "문서 내용을 입력해주세요.")
        String content
) {
}
