package com.fandom.feed.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "내용을 입력해주세요.")
        @Size(max = 200, message = "내용은 200자 이내로 작성해주세요.")
        String content
) {}