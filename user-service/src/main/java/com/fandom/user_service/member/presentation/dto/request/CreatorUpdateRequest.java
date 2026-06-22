package com.fandom.user_service.member.presentation.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 크리에이터 계정 정보 수정 요청.
 * null 필드는 수정하지 않고, 빈 문자열/공백 문자열은 허용하지 않는다.
 */
public record CreatorUpdateRequest(

        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @Size(max = 50, message = "소속사명은 50자 이하여야 합니다.")
        String agencyName,

        @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
        String zipCode,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address1,

        @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
        String address2
) {

    @AssertTrue(message = "변경할 정보가 하나 이상 필요합니다.")
    public boolean hasAnyField() {
        return password != null
                || email != null
                || agencyName != null
                || zipCode != null
                || address1 != null
                || address2 != null;
    }

    @AssertTrue(message = "빈 문자열은 입력할 수 없습니다.")
    public boolean hasNoBlankField() {
        return isNullOrNotBlank(password)
                && isNullOrNotBlank(email)
                && isNullOrNotBlank(agencyName)
                && isNullOrNotBlank(zipCode)
                && isNullOrNotBlank(address1)
                && isNullOrNotBlank(address2);
    }

    private boolean isNullOrNotBlank(String value) {
        return value == null || !value.isBlank();
    }
}
