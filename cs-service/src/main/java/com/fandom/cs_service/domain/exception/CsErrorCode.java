package com.fandom.cs_service.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CsErrorCode implements ErrorCode {

    CS_ANSWER_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "답변 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
