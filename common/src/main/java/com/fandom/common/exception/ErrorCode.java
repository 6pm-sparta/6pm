package com.fandom.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인별 ErrorCode enum이 구현해야 하는 인터페이스.
 * common의 CommonErrorCode, 각 도메인의 MemberErrorCode 등이 이를 구현한다.
 */
public interface ErrorCode {
    HttpStatus getStatus();
    String getMessage();
}
