package com.fandom.gateway_service.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 클라이언트가 임의로 주입한 내부 인증 헤더를 제거하는 글로벌 필터.
 *
 * UserIdCard 신원 헤더(X-Id-Card 등)는 오직 Gateway만 생성·전파해야 한다.
 * 외부에서 들어온 동일 이름의 헤더를 그대로 통과시키면 클라이언트가 신원을 위조할 수 있으므로,
 * 라우팅 이전 단계에서 선제적으로 제거한다. (Zero Trust / Defense in Depth)
 *
 * 가장 먼저 실행되어야 하므로 우선순위를 최상위로 둔다.
 */
@Component
public class HeaderSanitizationFilter implements GlobalFilter, Ordered {

    // 클라이언트가 보내면 안 되는(=Gateway만 생성하는) 내부 인증 헤더
    private static final List<String> FORBIDDEN_EXACT_HEADERS = List.of(
            "X-Id-Card",
            "X-Id-Card-Signature"
    );

    // 아래 접두사로 시작하는 모든 헤더도 내부 전용이므로 제거한다.
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "X-Internal-",
            "X-User-"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    FORBIDDEN_EXACT_HEADERS.forEach(headers::remove);
                    headers.keySet().removeIf(this::isForbiddenByPrefix);
                })
                .build();

        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    private boolean isForbiddenByPrefix(String headerName) {
        return FORBIDDEN_PREFIXES.stream()
                .anyMatch(prefix -> headerName.regionMatches(true, 0, prefix, 0, prefix.length()));
    }

    @Override
    public int getOrder() {
        // 다른 어떤 필터(특히 인증/전파 필터)보다 먼저 실행되어 위조 헤더를 선제 제거한다.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
