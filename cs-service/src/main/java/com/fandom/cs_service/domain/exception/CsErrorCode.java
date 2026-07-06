package com.fandom.cs_service.domain.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CsErrorCode implements ErrorCode {

    CS_ANSWER_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "답변 생성에 실패했습니다."),
    CS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CS_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
