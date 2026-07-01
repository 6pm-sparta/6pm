package com.fandom.gateway_service.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GatewayErrorCode implements ErrorCode {

    AUTH_STATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 상태를 확인할 수 없습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해 주세요."),
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "요청 처리 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
