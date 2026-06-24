package com.fandom.gateway_service.filter;

import com.fandom.common.auth.UserIdCard;
import com.fandom.gateway_service.security.GatewayAuthenticationAttributes;
import com.fandom.gateway_service.security.GatewaySecurityRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RbacAuthorizationFilter unit tests")
class RbacAuthorizationFilterTest {

    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);
    private final RbacAuthorizationFilter filter = new RbacAuthorizationFilter(
            new GatewaySecurityRules(),
            new ObjectMapper()
    );

    @Test
    @DisplayName("permitAll request passes without id card")
    void permitAll_pass() {
        given(chain.filter(any())).willReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("missing id card returns 401")
    void missingIdCard_unauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/members/me").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("member cannot access creator profile update")
    void forbiddenRole_forbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/creators/me/profile").build());
        exchange.getAttributes().put(
                GatewayAuthenticationAttributes.USER_ID_CARD,
                UserIdCard.of(UUID.randomUUID(), "MEMBER")
        );

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("creator can access creator profile update")
    void allowedRole_pass() {
        given(chain.filter(any())).willReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/creators/me/profile").build());
        exchange.getAttributes().put(
                GatewayAuthenticationAttributes.USER_ID_CARD,
                UserIdCard.of(UUID.randomUUID(), "CREATOR")
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
