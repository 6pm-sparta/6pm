package com.fandom.gateway_service.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Gateway AccessLogFilter 단위 테스트")
class AccessLogFilterTest {

    private static final String TRACE_ID = "6a44aae8d8efd11a2216e37fdbad4c96";
    private static final String SPAN_ID = "6c8aa8132af09e91";

    @Test
    @DisplayName("현재 span이 있으면 응답 헤더에 traceId를 노출한다")
    void addsTraceIdResponseHeader() {
        Tracer tracer = mockTracer(TRACE_ID, SPAN_ID);
        AccessLogFilter filter = new AccessLogFilter(tracer);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();
        exchange.getResponse().setComplete().block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(TRACE_ID);
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("현재 span이 없으면 응답 헤더에 traceId를 노출하지 않는다")
    void skipsTraceIdResponseHeaderWithoutCurrentSpan() {
        Tracer tracer = mock(Tracer.class);
        AccessLogFilter filter = new AccessLogFilter(tracer);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        filter.filter(exchange, chain).block();
        exchange.getResponse().setComplete().block();

        assertThat(exchange.getResponse().getHeaders().containsKey("X-Trace-Id")).isFalse();
        verify(chain).filter(exchange);
    }

    private Tracer mockTracer(String traceId, String spanId) {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        given(tracer.currentSpan()).willReturn(span);
        given(span.context()).willReturn(context);
        given(context.traceId()).willReturn(traceId);
        given(context.spanId()).willReturn(spanId);
        return tracer;
    }
}
