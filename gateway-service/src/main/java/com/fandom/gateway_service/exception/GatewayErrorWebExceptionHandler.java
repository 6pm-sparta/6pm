package com.fandom.gateway_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandom.common.dto.ApiResponse;
import io.micrometer.tracing.Tracer;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracerProvider;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!isTimeoutException(ex)) {
            return Mono.error(ex);
        }

        GatewayErrorCode errorCode = GatewayErrorCode.GATEWAY_TIMEOUT;
        String traceId = currentTraceId();
        log.warn("[TIMEOUT] downstream 응답 대기 시간이 초과되었습니다. path={}, traceId={}",
                exchange.getRequest().getPath().value(), traceId);

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.setStatusCode(errorCode.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (traceId != null) {
            response.getHeaders().set("X-Trace-Id", traceId);
        }

        ApiResponse<Void> body = ApiResponse.error(errorCode.getStatus(), errorCode.getMessage());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationException) {
            log.warn("Gateway timeout 응답 직렬화 실패", serializationException);
            return response.setComplete();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof ReadTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String currentTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null || tracer.currentSpan() == null) {
            return null;
        }
        return tracer.currentSpan().context().traceId();
    }
}
