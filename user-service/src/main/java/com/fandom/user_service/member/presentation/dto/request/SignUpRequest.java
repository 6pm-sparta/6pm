package com.fandom.user_service.member.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 일반회원 가입 요청.
 */
public record SignUpRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname,

        @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
        String zipCode,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address1,

        @Size(max = 255, message = "상세주소는 255자 이하여야 합니다.")
        String address2
) {
}
