package com.fandom.gateway_service.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.exception.GatewayErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.ConnectException;

/**
 * Circuit Breaker가 open되거나 downstream 호출이 실패했을 때 forward되는 공통 fallback 엔드포인트.
 *
 * route에 붙은 CircuitBreaker 필터가 열리면 fallbackUri(forward:/fallback)로 요청이 넘어오고,
 * 여기서 503(SERVICE_UNAVAILABLE)을 공통 응답 형식(ApiResponse)으로 반환한다.
 * 이렇게 하면 특정 downstream 장애가 Gateway 전체 장애(500 등)로 전파되지 않고,
 * 클라이언트는 일관된 형식의 503을 받는다.
 *
 * (참고: CB가 적용된 라우트에서는 downstream 응답 지연이 timelimiter 설정을 초과해도
 *  GatewayErrorWebExceptionHandler(504 담당)가 아니라 이 fallback으로 먼저 들어온다.
 *  CircuitBreaker 필터가 TimeLimiter와 함께 묶여 동작하면서, 타임아웃을 먼저 잡아
 *  자체 fallbackUri로 포워딩하기 때문이다(2026-07-08 로컬 재현으로 확인).
 *  이 fallback 자체가 reason에 따라 응답 상태코드를 달리 가져간다:
 *  reason=TIMEOUT이면 의미상 맞는 504(GATEWAY_TIMEOUT)를, 그 외(CB open/인스턴스 부재 등)는
 *  503(SERVICE_UNAVAILABLE)을 반환한다.
 *  (CB가 안 붙은 경로에서만 순수 httpclient 레벨 타임아웃이 GatewayErrorWebExceptionHandler를 타고 504로 빠진다.
 *  현재 모든 주요 라우트에 CircuitBreaker가 붙어있어, 실제로는 그 경로가 거의 발생하지 않고,
 *  대부분의 타임아웃은 이 fallback을 통해 504로 반환된다.)
 *
 * 원인 분류(X-Fallback-Reason)는 exchange attribute로 실제 확인된 키를 기반으로 한다.
 * (2026-07-08 로컬 재현으로 키/타입 확인. Spring Cloud Gateway 4.3.0 기준.
 *  ServerWebExchangeUtils의 상수명이 버전에 따라 바뀔 수 있어 문자열 리터럴로 직접 참조한다.)
 *
 * traceId는 tracing이 활성화돼 있으면 응답 헤더(X-Trace-Id)로 노출한다.
 * 없으면(현재 span이 없으면) 생략한다. tracing 미적용 환경에서도 안전하게 동작한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FallbackController {

    // ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR 에 해당하는 실제 키 값.
    // (로컬 재현으로 확인: CircuitBreaker 필터가 fallback으로 forward하면서 원인 예외를 여기에 담는다.)
    private static final String CB_EXECUTION_EXCEPTION_ATTR =
            "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.circuitBreakerExecutionException";

    // ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR 에 해당하는 실제 키 값.
    // (어느 라우트에서 fallback이 발생했는지 식별용, 예: "user-service")
    private static final String ROUTE_ID_ATTR =
            "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateMatchedPathRouteIdAttr";

    private final ObjectProvider<Tracer> tracerProvider;

    @RequestMapping("/fallback")
    public ResponseEntity<ApiResponse<Void>> fallback(ServerWebExchange exchange) {
        String traceId = currentTraceId();

        Throwable cause = exchange.getAttribute(CB_EXECUTION_EXCEPTION_ATTR);
        String routeId = exchange.getAttribute(ROUTE_ID_ATTR);
        String reason = classifyReason(cause);
        GatewayErrorCode errorCode = "TIMEOUT".equals(reason)
                ? GatewayErrorCode.GATEWAY_TIMEOUT
                : GatewayErrorCode.SERVICE_UNAVAILABLE;

        log.warn("[FALLBACK] route={} reason={} status={} traceId={} cause={}",
                routeId, reason, errorCode.getStatus().value(), traceId, describeCause(cause));

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus())
                .header("X-Fallback-Reason", reason);
        if (routeId != null) {
            builder.header("X-Fallback-Route", routeId);
        }
        if (traceId != null) {
            builder.header("X-Trace-Id", traceId);
        }
        return builder.body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
    }

    /**
     * fallback을 유발한 실제 원인을 분류한다.
     * - CIRCUIT_OPEN: CB가 이미 OPEN 상태라 downstream 호출 자체를 시도하지 않음
     * - TIMEOUT: downstream은 호출되었으나 timelimiter 설정(10s 등) 안에 응답이 안 옴
     * - NO_INSTANCE_AVAILABLE: Eureka/LoadBalancer가 해당 서비스의 인스턴스를 못 찾음
     *   (예: 인스턴스가 라우팅 불가능한 주소로 등록된 경우 등)
     * - CONNECTION_FAILED: 인스턴스는 찾았지만 실제 연결 자체가 거부/실패함
     * - DOWNSTREAM_ERROR: 그 외 downstream에서 발생한 예외
     * - UNKNOWN: 원인 예외 정보를 못 가져온 경우
     */
    private String classifyReason(Throwable cause) {
        if (cause == null) {
            return "UNKNOWN";
        }
        if (cause instanceof CallNotPermittedException) {
            return "CIRCUIT_OPEN";
        }
        if (isTimeoutException(cause)) {
            return "TIMEOUT";
        }
        if (cause instanceof NotFoundException) {
            return "NO_INSTANCE_AVAILABLE";
        }
        if (cause instanceof ConnectException) {
            return "CONNECTION_FAILED";
        }
        return "DOWNSTREAM_ERROR";
    }

    /**
     * 타임아웃 계열 예외를 클래스명 기준으로 판별한다.
     * java.util.concurrent.TimeoutException, Netty ReadTimeoutException, Reactor/Spring Cloud
     * Gateway가 자체적으로 던지는 TimeoutException 등 구현체가 여러 개라 타입으로
     * 직접 비교하는 대신 클래스명에 "TimeoutException"이 포함되는지로 판별한다
     * (2026-07-08 로컬 재현 시 실제 메시지: "TimeoutException: Did not observe any item...").
     */
    private boolean isTimeoutException(Throwable cause) {
        return cause.getClass().getSimpleName().contains("TimeoutException");
    }

    private String describeCause(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
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
