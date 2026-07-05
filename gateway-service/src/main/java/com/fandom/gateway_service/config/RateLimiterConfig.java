package com.fandom.gateway_service.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiting 키 전략.
 *
 * 인증 전 요청(로그인/재발급)은 userId를 알 수 없으므로 "IP + path" 조합을 키로 사용한다.
 * - IP: 동일 출발지의 brute-force/반복 요청을 제한하기 위한 기준
 * - path: 같은 IP라도 경로별로 버킷을 분리(로그인 제한이 재발급 제한과 독립적으로 동작)
 *
 * IP 추출 전략: XForwardedRemoteAddressResolver.maxTrustedIndex(1)
 * - 운영(ALB 경유): X-Forwarded-For 헤더의 마지막 1홉(ALB가 붙인 값)을 신뢰해 실제 클라이언트 IP 추출.
 *   클라이언트가 헤더를 위조해도 ALB가 덮어쓰므로 우회 불가.
 * - 로컬(직접 연결): X-Forwarded-For 헤더 없으면 remoteAddress로 자동 fallback.
 *   환경별 별도 코드 불필요.
 */
@Configuration
public class RateLimiterConfig {

    private static final XForwardedRemoteAddressResolver REMOTE_ADDRESS_RESOLVER =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    public KeyResolver ipAndPathKeyResolver() {
        return exchange -> {
            InetSocketAddress address = REMOTE_ADDRESS_RESOLVER.resolve(exchange);
            String ip = (address != null && address.getAddress() != null)
                    ? address.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip + ":" + exchange.getRequest().getPath().value());
        };
    }
}
