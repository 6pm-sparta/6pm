package com.fandom.gateway_service.filter;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.security.GatewayAuthenticationAttributes;
import com.fandom.gateway_service.security.GatewaySecurityRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RbacAuthorizationFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityRules gatewaySecurityRules;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (gatewaySecurityRules.isPermitAll(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        UserIdCard idCard = exchange.getAttribute(GatewayAuthenticationAttributes.USER_ID_CARD);
        if (idCard == null) {
            return unauthorized(exchange);
        }

        if (!gatewaySecurityRules.isAllowed(exchange.getRequest(), idCard.getRole())) {
            return forbidden(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(status, message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            return response.setComplete();
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
