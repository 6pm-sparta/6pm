package com.fandom.notification_service.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "기기 토큰을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
