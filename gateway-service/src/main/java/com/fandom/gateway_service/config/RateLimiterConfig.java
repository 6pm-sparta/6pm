package com.fandom.gateway_service.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiting 키 전략.
 *
 * 인증 전 요청(로그인/재발급)은 userId를 알 수 없으므로 "IP + path" 조합을 키로 사용한다.
 * - IP: 동일 출발지의 brute-force/반복 요청을 제한하기 위한 기준
 * - path: 같은 IP라도 경로별로 버킷을 분리(로그인 제한이 재발급 제한과 독립적으로 동작)
 *
 * 주의(후속): 현재는 remoteAddress 기반이다. 운영에서 L4/L7 로드밸런서(ALB) 뒤에 놓이면
 * 실제 클라이언트 IP가 X-Forwarded-For 헤더에 담기므로, 그때는 XForwardedRemoteAddressResolver
 * 등으로 전환해야 한다. (로컬/1차 범위에서는 remoteAddress로 충분)
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipAndPathKeyResolver() {
        return exchange -> Mono.just(resolveIp(exchange) + ":" + exchange.getRequest().getPath().value());
    }

    private String resolveIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
