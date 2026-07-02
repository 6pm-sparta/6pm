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

        exchange.getResponse().beforeCommit(() -> {
            TraceIds traceIds = currentTraceIds();
            if (traceIds.hasTraceId()) {
                exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceIds.traceId());
            }
            return Mono.empty();
        });

        return chain.filter(exchange)
                .doFinally(signalType -> logAccess(exchange, request, startedAt));
    }

    private void logAccess(ServerWebExchange exchange, ServerHttpRequest request, long startedAt) {
        long elapsedMillis = System.currentTimeMillis() - startedAt;
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        TraceIds traceIds = currentTraceIds();

        log.info("[ACCESS] traceId={} spanId={} method={} path={} status={} elapsedTimeMs={}",
                traceIds.traceId(),
                traceIds.spanId(),
                request.getMethod(),
                request.getPath(),
                status != null ? status.value() : "UNKNOWN",
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
