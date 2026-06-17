package com.fandom.user_service.profile.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 프로필 도메인 에러코드. common의 ErrorCode 인터페이스를 구현한다.
 */
@Getter
@AllArgsConstructor
public enum ProfileErrorCode implements ErrorCode {

    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");

    private final HttpStatus status;
    private final String message;
}
