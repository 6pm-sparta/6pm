package com.fandom.user_service.profile.presentation.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatorProfileUpdateRequest(

        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname,

        @PastOrPresent(message = "생일은 미래 날짜일 수 없습니다.")
        LocalDate birthday,

        @Size(max = 1000, message = "상태 메시지는 1000자 이하여야 합니다.")
        String profileMessage,

        @Size(max = 255, message = "프로필 이미지 URL은 255자 이하여야 합니다.")
        String profileImage,

        @Size(max = 255, message = "배너 이미지 URL은 255자 이하여야 합니다.")
        String bannerImage
) {

    @AssertTrue(message = "변경할 프로필 정보가 하나 이상 필요합니다.")
    public boolean hasAnyField() {
        return nickname != null
                || birthday != null
                || profileMessage != null
                || profileImage != null
                || bannerImage != null;
    }

    @AssertTrue(message = "빈 문자열은 입력할 수 없습니다.")
    public boolean hasNoBlankField() {
        return isNullOrNotBlank(nickname)
                && isNullOrNotBlank(profileMessage)
                && isNullOrNotBlank(profileImage)
                && isNullOrNotBlank(bannerImage);
    }

    private boolean isNullOrNotBlank(String value) {
        return value == null || !value.isBlank();
    }
}
