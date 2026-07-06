package com.fandom.chat_service.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 채팅방에 접근 권한이 없습니다."),
    CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "메시지가 너무 깁니다."),
    CHAT_SLOW_MODE(HttpStatus.TOO_MANY_REQUESTS, "슬로우 모드입니다. 잠시 후 다시 보내주세요."),
    CHAT_DUPLICATE_MESSAGE(HttpStatus.TOO_MANY_REQUESTS, "동일한 메시지를 연속으로 보낼 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
