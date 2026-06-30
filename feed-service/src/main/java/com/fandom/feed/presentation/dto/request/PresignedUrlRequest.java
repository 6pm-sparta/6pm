package com.fandom.feed.presentation.dto.request;

import com.fandom.feed.presentation.dto.request.validation.ValidImageName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignedUrlRequest(
        @NotEmpty(message = "이미지를 선택해주세요.")
        @Size(max = 4, message = "이미지는 최대 4개까지 가능합니다.")
        List<@ValidImageName String> imageNames
) {}