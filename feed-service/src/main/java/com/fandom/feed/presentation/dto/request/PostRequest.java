package com.fandom.feed.presentation.dto.request;

import com.fandom.feed.presentation.dto.request.validation.ValidImageKey;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostRequest(
        @Size(max = 1000, message = "내용은 1,000자 이내로 작성해주세요.")
        String content,

        @Size(max = 4, message = "이미지는 최대 4개까지 가능합니다.")
        List<@ValidImageKey String> imageKeys
) {
    public PostRequest {
        imageKeys = (imageKeys == null) ? List.of() : imageKeys; // null → 빈 리스트
    }

    @AssertTrue(message = "내용 또는 이미지를 하나 이상 포함해주세요.")
    public boolean isContentOrImagePresent() {
        return content != null || !imageKeys.isEmpty();
    }
}