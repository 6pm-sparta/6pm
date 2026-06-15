package com.fandom.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드.
 * 도메인별 에러코드는 각 도메인 패키지의 XXXErrorCode enum에 정의한다.
 */
@Getter
@AllArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INVALID_ID_CARD(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자 인증 정보입니다.");

    private final HttpStatus status;
    private final String message;
}
