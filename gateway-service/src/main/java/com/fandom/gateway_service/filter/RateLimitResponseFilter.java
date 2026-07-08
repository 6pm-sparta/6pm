package com.fandom.gateway_service.filter;

import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.exception.GatewayErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * RequestRateLimiter가 요청을 거부(429)했을 때, 응답 바디를 공통 형식(ApiResponse)으로 채운다.
 *
 * RequestRateLimiter는 제한 초과 시 예외를 던지지 않고 응답 상태만 429로 세팅한 뒤 (바디 없이) 종료한다.
 * 이때 beforeCommit 훅에서 새 바디를 write하는 방식은 응답이 이미 Content-Length=0 등으로 확정돼 동작하지 않는다.
 * 따라서 응답을 ServerHttpResponseDecorator로 감싸 writeWith 시점에 상태코드가 429면 바디를 우리 형식으로 교체한다.
 *
 * 429 발생 시 클라이언트 IP와 경로를 WARN 로그로 남긴다. (brute-force/과부하 신호 추적)
 * IP는 RateLimiterConfig와 동일하게 XForwardedRemoteAddressResolver(maxTrustedIndex=1)로 추출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    private static final XForwardedRemoteAddressResolver REMOTE_ADDRESS_RESOLVER =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return writeTooManyRequestsBody(getDelegate(), exchange);
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                // RequestRateLimiter가 바디 없이 setComplete()로 끝내는 경우에도 429 바디를 채운다.
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return writeTooManyRequestsBody(getDelegate(), exchange);
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private Mono<Void> writeTooManyRequestsBody(ServerHttpResponse response, ServerWebExchange exchange) {
        logRateLimit(exchange);

        GatewayErrorCode errorCode = GatewayErrorCode.TOO_MANY_REQUESTS;
        ApiResponse<Void> body = ApiResponse.error(errorCode.getStatus(), errorCode.getMessage());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            log.warn("[RATE-LIMIT] 429 응답 바디 직렬화 실패", e);
            return response.setComplete();
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Flux.just(buffer));
    }

    private void logRateLimit(ServerWebExchange exchange) {
        InetSocketAddress address = REMOTE_ADDRESS_RESOLVER.resolve(exchange);
        String ip = (address != null && address.getAddress() != null)
                ? address.getAddress().getHostAddress()
                : "unknown";
        String path = exchange.getRequest().getPath().value();
        log.warn("[RATE-LIMIT] 429 Too Many Requests. ip={} path={}", ip, path);
    }

    @Override
    public int getOrder() {
        // 응답을 데코레이터로 감싸야 하므로 다른 필터보다 먼저(바깥) 실행되어야 한다.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
