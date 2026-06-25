package com.fandom.common.exception;

import com.fandom.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 우리가 의도적으로 던진 비즈니스 예외 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        // AIOps 로그 수집용 (경고 수준)
        log.warn("[CustomException] errCode: {}, message: {}", errorCode, errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
    }

    // 2. @Valid 검증 실패 시 발생하는 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("[ValidationException] message: {}", errorMessage);

        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE.getStatus(), errorMessage));
    }

    // 3. 필수 헤더 누락 (@RequestHeader)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException e) {
        String message = CommonErrorCode.MISSING_REQUIRED_HEADER.getMessage() + ": " + e.getHeaderName();
        log.warn("[MissingRequestHeaderException] header: {}", e.getHeaderName());
        return ResponseEntity
                .status(CommonErrorCode.MISSING_REQUIRED_HEADER.getStatus())
                .body(ApiResponse.error(CommonErrorCode.MISSING_REQUIRED_HEADER.getStatus(), message));
    }

    // 4. 필수 쿼리 파라미터 누락 (@RequestParam)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParam(MissingServletRequestParameterException e) {
        String message = CommonErrorCode.MISSING_REQUIRED_PARAMETER.getMessage() + ": " + e.getParameterName();
        log.warn("[MissingServletRequestParameterException] param: {}", e.getParameterName());
        return ResponseEntity
                .status(CommonErrorCode.MISSING_REQUIRED_PARAMETER.getStatus())
                .body(ApiResponse.error(CommonErrorCode.MISSING_REQUIRED_PARAMETER.getStatus(), message));
    }

    // 5. HTTP body 파싱 실패 (JSON 형식 오류 등)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] message: {}", e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_REQUEST_BODY.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_REQUEST_BODY.getStatus(), CommonErrorCode.INVALID_REQUEST_BODY.getMessage()));
    }

    // 6. 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[HttpRequestMethodNotSupportedException] method: {}", e.getMethod());
        return ResponseEntity
                .status(CommonErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED.getStatus(), CommonErrorCode.METHOD_NOT_ALLOWED.getMessage()));
    }

    // 7. 미처 잡지 못한 모든 런타임 예외 (최후의 방어선)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[UnexpectedException] message: {}", e.getMessage(), e);

        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus(), CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
