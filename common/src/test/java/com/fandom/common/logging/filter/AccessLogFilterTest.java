package com.fandom.common.logging.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Common AccessLogFilter 단위 테스트")
class AccessLogFilterTest {

    @Test
    @DisplayName("Tracer가 없어도 요청 처리를 계속 진행한다")
    void continuesWithoutTracer() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(emptyProvider());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("현재 span이 있으면 요청 처리를 계속 진행한다")
    void continuesWithCurrentSpan() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(provider(mockTracer()));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();

        verify(chain).doFilter(any(), any());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Tracer> emptyProvider() {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Tracer> provider(Tracer tracer) {
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
        given(context.traceId()).willReturn("6a44aae8d8efd11a2216e37fdbad4c96");
        given(context.spanId()).willReturn("6c8aa8132af09e91");
        return tracer;
    }
}
