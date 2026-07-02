package com.fandom.gateway_service.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.exception.GatewayErrorCode;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit Breaker가 open되거나 downstream 호출이 실패했을 때 forward되는 공통 fallback 엔드포인트.
 *
 * route에 붙은 CircuitBreaker 필터가 열리면 fallbackUri(forward:/fallback)로 요청이 넘어오고,
 * 여기서 503(SERVICE_UNAVAILABLE)을 공통 응답 형식(ApiResponse)으로 반환한다.
 * 이렇게 하면 특정 downstream 장애가 Gateway 전체 장애(500 등)로 전파되지 않고,
 * 클라이언트는 일관된 형식의 503을 받는다.
 *
 * (참고: 응답 대기 timeout 초과는 Gateway httpclient 레벨에서 504로 처리되며 이 fallback을 타지 않는다.)
 *
 * traceId는 tracing이 활성화돼 있으면 응답 헤더(X-Trace-Id)로 노출한다.
 * 없으면(현재 span이 없으면) 생략한다. tracing 미적용 환경에서도 안전하게 동작한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FallbackController {

    private final ObjectProvider<Tracer> tracerProvider;

    @RequestMapping("/fallback")
    public ResponseEntity<ApiResponse<Void>> fallback() {
        GatewayErrorCode errorCode = GatewayErrorCode.SERVICE_UNAVAILABLE;

        String traceId = currentTraceId();
        log.warn("[FALLBACK] Circuit Breaker open 또는 downstream 실패로 fallback 응답 반환. traceId={}", traceId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus());
        if (traceId != null) {
            builder.header("X-Trace-Id", traceId);
        }
        return builder.body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
    }

    /** 현재 tracing span의 traceId. tracing 미활성/컨텍스트 없음이면 null. */
    private String currentTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null || tracer.currentSpan() == null) {
            return null;
        }
        return tracer.currentSpan().context().traceId();
    }
}
