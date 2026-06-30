package com.fandom.gateway_service.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.ErrorCode;
import com.fandom.gateway_service.exception.GatewayErrorCode;
import com.fandom.gateway_service.jwt.JwtValidator;
import com.fandom.gateway_service.security.GatewayAuthenticationAttributes;
import com.fandom.gateway_service.security.GatewaySecurityRules;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_BLACKLIST_KEY_PREFIX = "blacklist:access:";
    private static final String USER_BLACKLIST_KEY_PREFIX = "blacklist:user:";

    private final JwtValidator jwtValidator;
    private final HmacUtils hmacUtils;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySecurityRules gatewaySecurityRules;

    public JwtAuthenticationFilter(JwtValidator jwtValidator, HmacUtils hmacUtils,
                                   ObjectMapper objectMapper, ReactiveStringRedisTemplate redisTemplate,
                                   GatewaySecurityRules gatewaySecurityRules) {
        this.jwtValidator = jwtValidator;
        this.hmacUtils = hmacUtils;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.gatewaySecurityRules = gatewaySecurityRules;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (gatewaySecurityRules.isPermitAll(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, CommonErrorCode.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtValidator.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return writeErrorResponse(exchange, CommonErrorCode.INVALID_ID_CARD);
        }

        String jti = claims.getId();
        String userId = claims.getSubject();
        Mono<Boolean> accessBlacklisted = redisTemplate.hasKey(ACCESS_BLACKLIST_KEY_PREFIX + jti)
                .defaultIfEmpty(false);
        Mono<Boolean> userBlacklisted = redisTemplate.hasKey(USER_BLACKLIST_KEY_PREFIX + userId)
                .defaultIfEmpty(false);

        return Mono.zip(accessBlacklisted, userBlacklisted)
                .materialize()
                .flatMap(result -> {
                    if (result.isOnError()) {
                        log.warn("인증 상태 조회 실패", result.getThrowable());
                        return writeErrorResponse(exchange, GatewayErrorCode.AUTH_STATE_UNAVAILABLE);
                    }
                    var tuple = result.get();
                    if (Boolean.TRUE.equals(tuple.getT1()) || Boolean.TRUE.equals(tuple.getT2())) {
                        return writeErrorResponse(exchange, CommonErrorCode.INVALID_ID_CARD);
                    }
                    UserIdCard idCard = toUserIdCard(claims);
                    ServerHttpRequest mutatedRequest = withUserIdCard(request, idCard);
                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                    mutatedExchange.getAttributes().put(GatewayAuthenticationAttributes.USER_ID_CARD, idCard);
                    return chain.filter(mutatedExchange);
                });
    }

    private UserIdCard toUserIdCard(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);
        return UserIdCard.of(userId, role);
    }

    private ServerHttpRequest withUserIdCard(ServerHttpRequest request, UserIdCard idCard) {
        String idCardJson = objectMapperWriteValue(idCard);
        String signature = hmacUtils.sign(idCard);

        return request.mutate()
                .header(IdCardVerificationFilter.ID_CARD_HEADER, idCardJson)
                .header(IdCardVerificationFilter.ID_CARD_SIGNATURE_HEADER, signature)
                .build();
    }

    private String objectMapperWriteValue(UserIdCard idCard) {
        try {
            return objectMapper.writeValueAsString(idCard);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize UserIdCard", e);
        }
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(errorCode.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(errorCode.getStatus(), errorCode.getMessage());
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
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
