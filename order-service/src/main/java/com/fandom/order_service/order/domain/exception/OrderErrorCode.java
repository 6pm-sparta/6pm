package com.fandom.order_service.order.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 주문 도메인 에러코드. common의 ErrorCode 인터페이스를 구현한다.
 */
@Getter
@AllArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 주문만 조회할 수 있습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "취소할 수 없는 주문 상태입니다."),
    CANCELLATION_WINDOW_EXPIRED(HttpStatus.CONFLICT, "취소 가능 시간이 지난 주문입니다.");

    private final HttpStatus status;
    private final String message;
}
