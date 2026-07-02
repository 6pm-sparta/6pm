package com.fandom.common.logging.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {

    private final ObjectProvider<Tracer> tracerProvider;

    public AccessLogFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = System.currentTimeMillis() - startedAt;
            TraceIds traceIds = currentTraceIds();

            log.info("[ACCESS] traceId={} spanId={} method={} path={} status={} elapsedTimeMs={}",
                    traceIds.traceId(),
                    traceIds.spanId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis);
        }
    }

    private TraceIds currentTraceIds() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return TraceIds.empty();
        }

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
    }
}
