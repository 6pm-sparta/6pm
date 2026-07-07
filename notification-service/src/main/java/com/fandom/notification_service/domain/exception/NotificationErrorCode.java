package com.fandom.notification_service.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "기기 토큰을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 알림에 접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}
