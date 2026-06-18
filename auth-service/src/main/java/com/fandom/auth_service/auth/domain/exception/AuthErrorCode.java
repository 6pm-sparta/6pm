package com.fandom.auth_service.auth.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 인증(로그인) 도메인 에러코드. common의 ErrorCode 인터페이스를 구현한다.
 */
@Getter
@AllArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // 이메일/비밀번호 불일치는 계정 존재 여부 노출을 막기 위해 동일 메시지로 통일한다.
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    MEMBER_LOOKUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "회원 정보 조회에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
