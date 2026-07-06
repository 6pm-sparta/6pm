package com.fandom.feed.presentation.dto.request;

import com.fandom.feed.presentation.dto.request.validation.ValidImageLimit;
import com.fandom.feed.presentation.dto.request.validation.ValidImageKey;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostRequest(
        @Size(max = 1000, message = "내용은 1,000자 이내로 작성해주세요.")
        String content,

        @ValidImageLimit
        List<@ValidImageKey String> imageKeys
) {
    public PostRequest {
        // 공백만 있는 경우 null 처리
        content = (content != null && content.isBlank()) ? null : content;

        // null인 경우 빈 리스트 처리
        imageKeys = (imageKeys == null) ? List.of() : imageKeys;
    }

    @AssertTrue(message = "내용 또는 이미지를 하나 이상 포함해주세요.")
    public boolean isContentOrImagePresent() {
        return content != null || !imageKeys.isEmpty();
    }
}