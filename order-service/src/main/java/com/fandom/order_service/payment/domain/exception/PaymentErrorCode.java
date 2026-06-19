package com.fandom.order_service.payment.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 결제 도메인 에러코드.
 */
@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "결제할 수 없는 주문 상태입니다."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "다른 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "동일한 결제 요청이 처리 중입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    PG_ERROR(HttpStatus.BAD_GATEWAY, "PG사 결제 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}