package com.fandom.gateway_service.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        // 알려진 제약(2026-07-08 로컬 재현으로 확인, 후속 이슈로 분리 예정):
        // CircuitBreaker의 fallbackUri(forward:/fallback)를 타는 요청은, 원래 라우트 실행이
        // "cancel" signal로 doFinally를 먼저 발동시키고, 그 이후에 별도로 /fallback이 디스패치되어
        // beforeCommit이 호출된다. 즉 하나의 요청처럼 보여도 실행 생명주기가 두 단계로 쪼개져 있어서,
        // doFinally 시점엔 아직 최종 상태(예: 504)가 확정되지 않은 상태(committedStatus=null)다.
        // 이 경우 status는 실제 값(200 등 오답)보다 안전한 "UNKNOWN"으로 표시한다.
        // (CB fallback을 타지 않는 정상/일반 에러 응답에서는 beforeCommit이 doFinally 이전에 정상적으로
        //  호출되어 정확한 상태코드가 찍힌다.)
        AtomicReference<HttpStatusCode> committedStatus = new AtomicReference<>();

        exchange.getResponse().beforeCommit(() -> {
            committedStatus.set(exchange.getResponse().getStatusCode());
            TraceIds traceIds = currentTraceIds();
            if (traceIds.hasTraceId()) {
                exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceIds.traceId());
            }
            return Mono.empty();
        });

        return chain.filter(exchange)
                .doFinally(signalType -> logAccess(request, startedAt, committedStatus.get()));
    }

    private void logAccess(ServerHttpRequest request, long startedAt, HttpStatusCode committedStatus) {
        long elapsedMillis = System.currentTimeMillis() - startedAt;
        TraceIds traceIds = currentTraceIds();

        log.info("[ACCESS] traceId={} spanId={} method={} path={} status={} elapsedTimeMs={}",
                traceIds.traceId(),
                traceIds.spanId(),
                request.getMethod(),
                request.getPath(),
                committedStatus != null ? committedStatus.value() : "UNKNOWN",
                elapsedMillis);
    }

    private TraceIds currentTraceIds() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return TraceIds.empty();
        }

        return new TraceIds(currentSpan.context().traceId(), currentSpan.context().spanId());
    }

    private record TraceIds(String traceId, String spanId) {

        private static TraceIds empty() {
            return new TraceIds("-", "-");
        }

        private boolean hasTraceId() {
            return !"-".equals(traceId);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
