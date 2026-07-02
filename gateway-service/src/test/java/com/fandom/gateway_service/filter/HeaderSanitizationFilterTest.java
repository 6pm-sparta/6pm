package com.fandom.gateway_service.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * HeaderSanitizationFilter 단위 테스트.
 *
 * 클라이언트가 위조한 내부 인증 헤더(X-Id-Card, X-Id-Card-Signature, X-Internal-*, X-User-*)가
 * downstream으로 전달되기 전에 선제 제거되는지 검증한다. (Zero Trust / Defense in Depth)
 * 정상 헤더는 보존되어야 한다(과잉 제거 방지).
 */
@DisplayName("HeaderSanitizationFilter 단위 테스트")
class HeaderSanitizationFilterTest {

    private final HeaderSanitizationFilter filter = new HeaderSanitizationFilter();

    /**
     * 필터가 downstream(chain)으로 넘긴 "정제된 요청"을 캡처해서 반환한다.
     * chain.filter(exchange)의 인자에서 최종 요청 헤더를 꺼내 검증한다.
     */
    private ServerHttpRequest sanitizeAndCapture(MockServerHttpRequest request) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest();
    }

    @Test
    @DisplayName("X-Id-Card 헤더는 제거된다")
    void removes_idCard() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-Id-Card", "{\"userId\":\"forged\",\"role\":\"MASTER\"}")
                        .build());

        assertThat(sanitized.getHeaders().containsKey("X-Id-Card")).isFalse();
    }

    @Test
    @DisplayName("X-Id-Card-Signature 헤더는 제거된다")
    void removes_idCardSignature() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-Id-Card-Signature", "forged-signature")
                        .build());

        assertThat(sanitized.getHeaders().containsKey("X-Id-Card-Signature")).isFalse();
    }

    @Test
    @DisplayName("X-Internal-* 접두 헤더는 제거된다")
    void removes_internalPrefix() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-Internal-Token", "forged")
                        .header("X-Internal-Trace", "forged")
                        .build());

        assertThat(sanitized.getHeaders().containsKey("X-Internal-Token")).isFalse();
        assertThat(sanitized.getHeaders().containsKey("X-Internal-Trace")).isFalse();
    }

    @Test
    @DisplayName("X-User-* 접두 헤더는 제거된다")
    void removes_userPrefix() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-User-Id", "forged")
                        .header("X-User-Role", "MASTER")
                        .build());

        assertThat(sanitized.getHeaders().containsKey("X-User-Id")).isFalse();
        assertThat(sanitized.getHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    @DisplayName("대소문자가 다른 위조 헤더도 제거된다")
    void removes_caseInsensitive() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("x-id-card", "forged")
                        .header("X-INTERNAL-secret", "forged")
                        .header("x-user-id", "forged")
                        .build());

        assertThat(sanitized.getHeaders().containsKey("x-id-card")).isFalse();
        assertThat(sanitized.getHeaders().containsKey("X-INTERNAL-secret")).isFalse();
        assertThat(sanitized.getHeaders().containsKey("x-user-id")).isFalse();
    }

    @Test
    @DisplayName("정상 헤더(Authorization 등)는 보존된다")
    void preserves_normalHeaders() {
        ServerHttpRequest sanitized = sanitizeAndCapture(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("Authorization", "Bearer good-token")
                        .header("Content-Type", "application/json")
                        .header("X-Request-Id", "trace-123")   // X-Internal/X-User 접두가 아니므로 보존
                        .build());

        assertThat(sanitized.getHeaders().getFirst("Authorization")).isEqualTo("Bearer good-token");
        assertThat(sanitized.getHeaders().containsKey("Content-Type")).isTrue();
        assertThat(sanitized.getHeaders().containsKey("X-Request-Id")).isTrue();
    }

    @Test
    @DisplayName("위조 헤더를 제거한 뒤 정상적으로 다음 필터로 넘긴다")
    void passesToChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        given(chain.filter(any())).willReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-Id-Card", "forged").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("우선순위는 최상위(HIGHEST_PRECEDENCE)여서 다른 필터보다 먼저 실행된다")
    void order_isHighest() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
