package com.fandom.gateway_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterConfig KeyResolver 단위 테스트")
class RateLimiterConfigTest {

    private final KeyResolver keyResolver = new RateLimiterConfig().ipAndPathKeyResolver();

    @Test
    @DisplayName("IP와 path를 조합해 키를 생성한다")
    void resolvesKeyByIpAndPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new InetSocketAddress("192.168.0.10", 12345))
                        .build());

        String key = keyResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("192.168.0.10:/api/v1/auth/login");
    }

    @Test
    @DisplayName("같은 IP라도 path가 다르면 키가 분리된다")
    void separatesKeyByPath() {
        InetSocketAddress sameIp = new InetSocketAddress("192.168.0.10", 12345);
        MockServerWebExchange loginExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").remoteAddress(sameIp).build());
        MockServerWebExchange reissueExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/reissue").remoteAddress(sameIp).build());

        String loginKey = keyResolver.resolve(loginExchange).block();
        String reissueKey = keyResolver.resolve(reissueExchange).block();

        assertThat(loginKey).isNotEqualTo(reissueKey);
        assertThat(loginKey).endsWith("/api/v1/auth/login");
        assertThat(reissueKey).endsWith("/api/v1/auth/reissue");
    }

    @Test
    @DisplayName("remoteAddress가 없으면 unknown으로 대체한다")
    void fallsBackToUnknownWithoutRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        String key = keyResolver.resolve(exchange).block();

        assertThat(key).isEqualTo("unknown:/api/v1/auth/login");
    }
}
