package com.fandom.ticketing_service.common.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TicketingErrorCode implements ErrorCode {

    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 좌석입니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),
    NO_INVENTORY(HttpStatus.CONFLICT, "잔여 좌석이 없습니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "구매 한도를 초과했습니다."),
    ORDER_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "주문 생성에 실패했습니다."),
    SEAT_CONFIRM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "좌석 확정에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
