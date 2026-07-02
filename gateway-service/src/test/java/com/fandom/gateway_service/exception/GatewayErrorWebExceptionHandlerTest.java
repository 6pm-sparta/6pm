package com.fandom.gateway_service.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("GatewayErrorWebExceptionHandler 단위 테스트")
class GatewayErrorWebExceptionHandlerTest {

    private static final String TRACE_ID = "6a44aae8d8efd11a2216e37fdbad4c96";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("Gateway timeout 예외는 504와 공통 ApiResponse 형식으로 변환한다")
    void timeoutException_returnsGatewayTimeoutApiResponse() throws Exception {
        GatewayErrorWebExceptionHandler handler =
                new GatewayErrorWebExceptionHandler(objectMapper, emptyTracerProvider());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        handler.handle(exchange, new TimeoutException("response timeout")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/json");

        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("status").asInt()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(body.get("message").asText()).isEqualTo(GatewayErrorCode.GATEWAY_TIMEOUT.getMessage());
        assertThat(body.has("data")).isFalse();
    }

    @Test
    @DisplayName("현재 trace가 있으면 timeout 응답 헤더에 X-Trace-Id를 포함한다")
    void timeoutException_includesTraceIdHeaderWhenCurrentSpanExists() {
        GatewayErrorWebExceptionHandler handler =
                new GatewayErrorWebExceptionHandler(objectMapper, tracerProvider(mockTracer()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/feeds/posts").build());

        handler.handle(exchange, new TimeoutException()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("timeout이 아닌 예외는 기본 WebFlux 에러 처리로 위임한다")
    void nonTimeoutException_delegatesToNextExceptionHandler() {
        GatewayErrorWebExceptionHandler handler =
                new GatewayErrorWebExceptionHandler(objectMapper, emptyTracerProvider());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        RuntimeException exception = new RuntimeException("other failure");

        assertThatThrownBy(() -> handler.handle(exchange, exception).block())
                .isSameAs(exception);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Tracer> emptyTracerProvider() {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(tracer);
        return provider;
    }

    private Tracer mockTracer() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        given(tracer.currentSpan()).willReturn(span);
        given(span.context()).willReturn(context);
        given(context.traceId()).willReturn(TRACE_ID);
        return tracer;
    }
}
