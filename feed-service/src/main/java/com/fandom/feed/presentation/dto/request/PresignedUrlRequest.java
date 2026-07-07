package com.fandom.feed.presentation.dto.request;

import com.fandom.feed.presentation.dto.request.validation.ValidImageLimit;
import com.fandom.feed.presentation.dto.request.validation.ValidImageName;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PresignedUrlRequest(
        @NotEmpty(message = "이미지를 선택해주세요.")
        @ValidImageLimit
        List<@ValidImageName String> imageNames
) {}