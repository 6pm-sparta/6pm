package com.fandom.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// 데이터 필드가 null일 경우, JSON 결과물에서 제외하여 네트워크 트래픽(페이로드)을 최적화합니다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;             // HTTP 상태 코드 (예: 200, 201, 400, 500)
    private final String message;         // 응답 메시지 (예: "SUCCESS", "CREATED")
    private final T data;                 // 실제 반환할 결과 데이터 (제네릭)
    private final LocalDateTime timestamp;   // 에러 추적 및 분석용 발생 시간

    // -------------------------------------------------------------------------
    // 🟢 성공 응답 (Success Responses)
    // -------------------------------------------------------------------------

    // 1. 반환할 데이터가 없는 단순 성공 응답 (예: 삭제 성공, 로그아웃 성공)
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(HttpStatus.OK.value(), "SUCCESS", null, LocalDateTime.now());
    }

    // 2. 데이터를 포함하는 일반 성공 응답 (예: 단건 조회, 목록 조회)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), "SUCCESS", data, LocalDateTime.now());
    }

    // 3. 자원이 성공적으로 생성되었을 때의 응답 (예: 회원가입 완료, 게시글 등록 완료, 예매 성공)
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), "CREATED", data, LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // 🔴 에러 응답 (Error Responses) - GlobalExceptionHandler에서 내부적으로 사용
    // -------------------------------------------------------------------------
    public static <T> ApiResponse<T> error(HttpStatus status, String message) {
        return new ApiResponse<>(status.value(), message, null, LocalDateTime.now());
    }
}