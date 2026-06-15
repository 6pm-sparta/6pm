package com.fandom.common.exception;

import org.springframework.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // [공통 에러]
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // [유저 도메인 에러]
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // [티켓팅 도메인 에러]
    TICKET_SOLD_OUT(HttpStatus.BAD_REQUEST, "티켓이 모두 매진되었습니다."),
    ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예매한 내역이 존재합니다.");

    private final HttpStatus status;
    private final String message;
}