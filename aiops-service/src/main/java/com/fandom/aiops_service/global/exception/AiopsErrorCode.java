package com.fandom.aiops_service.global.exception;

import com.fandom.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiopsErrorCode implements ErrorCode {

    LLM_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LLM 장애 분석 호출에 실패했습니다."),
    INCIDENT_EVENT_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "장애 분석 이벤트 발행에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}