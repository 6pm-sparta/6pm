package com.fandom.gateway_service.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.exception.GatewayErrorCode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("FallbackController 단위 테스트")
class FallbackControllerTest {

    private static final String TRACE_ID = "6a44aae8d8efd11a2216e37fdbad4c96";

    @Test
    @DisplayName("fallback 응답은 503과 공통 ApiResponse 형식을 반환한다")
    void fallback_returnsServiceUnavailableApiResponse() {
        FallbackController controller = new FallbackController(emptyTracerProvider());

        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getBody().getMessage()).isEqualTo(GatewayErrorCode.SERVICE_UNAVAILABLE.getMessage());
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("현재 trace가 있으면 fallback 응답 헤더에 X-Trace-Id를 포함한다")
    void fallback_includesTraceIdHeaderWhenCurrentSpanExists() {
        FallbackController controller = new FallbackController(tracerProvider(mockTracer()));

        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("현재 trace가 없으면 fallback 응답 헤더에 X-Trace-Id를 포함하지 않는다")
    void fallback_omitsTraceIdHeaderWithoutCurrentSpan() {
        FallbackController controller = new FallbackController(emptyTracerProvider());

        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getHeaders().containsKey("X-Trace-Id")).isFalse();
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
