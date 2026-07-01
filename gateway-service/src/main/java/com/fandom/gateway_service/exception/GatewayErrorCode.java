package com.fandom.gateway_service.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GatewayErrorCode implements ErrorCode {

    AUTH_STATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 상태를 확인할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
