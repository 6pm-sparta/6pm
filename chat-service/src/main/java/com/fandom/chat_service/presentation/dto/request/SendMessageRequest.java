package com.fandom.chat_service.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
        String content
) {
}
