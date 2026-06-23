package com.fandom.gateway_service.filter;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.jwt.JwtValidator;
import com.fandom.gateway_service.security.GatewaySecurityRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Access Token을 검증하고, 검증된 신원을 UserIdCard로 만들어 downstream에 전파하는 글로벌 필터.
 *
 * HeaderSanitizationFilter가 클라이언트 위조 헤더를 먼저 제거한 뒤 실행된다.
 */
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

        // 인증 예외 경로는 토큰 검증 없이 통과한다.
        if (gatewaySecurityRules.isPermitAll(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "인증 토큰이 필요합니다.");
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtValidator.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 만료, 서명 불일치, 형식 오류, subject 변환 실패는 모두 인증 실패로 응답한다.
            return unauthorized(exchange, "유효하지 않은 토큰입니다.");
        }

        // 로그아웃 토큰과 사용자 단위 무효화 토큰을 함께 차단한다.
        String jti = claims.getId();
        String userId = claims.getSubject();
        Mono<Boolean> accessBlacklisted = redisTemplate.hasKey(ACCESS_BLACKLIST_KEY_PREFIX + jti)
                .defaultIfEmpty(false);
        Mono<Boolean> userBlacklisted = redisTemplate.hasKey(USER_BLACKLIST_KEY_PREFIX + userId)
                .defaultIfEmpty(false);

        return Mono.zip(accessBlacklisted, userBlacklisted)
                .flatMap(result -> {
                    if (Boolean.TRUE.equals(result.getT1()) || Boolean.TRUE.equals(result.getT2())) {
                        return unauthorized(exchange, "유효하지 않은 토큰입니다.");
                    }
                    ServerHttpRequest mutatedRequest = withUserIdCard(request, claims);
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    /**
     * 검증된 토큰 claim으로 UserIdCard를 만들고 HMAC 서명 헤더를 추가한다.
     */
    private ServerHttpRequest withUserIdCard(ServerHttpRequest request, Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);

        UserIdCard idCard = UserIdCard.of(userId, role);
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
            throw new IllegalStateException("UserIdCard 직렬화 실패", e);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(HttpStatus.UNAUTHORIZED, message);
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
