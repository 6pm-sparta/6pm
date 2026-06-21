package com.fandom.gateway_service.filter;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.dto.ApiResponse;
import com.fandom.gateway_service.jwt.JwtValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * 흐름 (common-auth.md):
 * 1. 인증 예외 경로(로그인 / 회원가입)는 검증 없이 통과
 * 2. 그 외 경로는 Authorization: Bearer 토큰 검증, 실패 시 401(ApiResponse 형식)
 * 3. 검증 성공 시 UserIdCard(userId, role) 생성 → HMAC 서명 → X-Id-Card / X-Id-Card-Signature 헤더로 전파
 *
 * HeaderSanitizationFilter(위조 헤더 제거) 다음에 실행되므로, 클라이언트가 보낸 X-Id-Card는 이미 제거된 상태다.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;
    private final HmacUtils hmacUtils;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtValidator jwtValidator, HmacUtils hmacUtils, ObjectMapper objectMapper) {
        this.jwtValidator = jwtValidator;
        this.hmacUtils = hmacUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 인증 예외 경로는 토큰 검증 없이 통과
        if (isPermitAll(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "인증 토큰이 필요합니다.");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtValidator.parse(token);
            ServerHttpRequest mutatedRequest = withUserIdCard(request, claims);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException: 만료/서명불일치/형식오류 등 토큰 자체의 문제
            // IllegalArgumentException: 빈 토큰 또는 subject(userId) UUID 변환 실패
            // 그 외(NPE 등 서버 오류)는 잡지 않아 '유효하지 않은 토큰'으로 둔갑하지 않는다.
            return unauthorized(exchange, "유효하지 않은 토큰입니다.");
        }
    }

    /**
     * 검증된 토큰의 claim으로 UserIdCard(userId, role)를 만들고 HMAC 서명하여
     * X-Id-Card / X-Id-Card-Signature 헤더로 실어 보낸다.
     *
     * 페이로드는 userId, role만 담는다. status는 인증 시점의 관심사이므로 IdCard에는 포함하지 않는다.
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

    /**
     * UserIdCard를 JSON 문자열로 직렬화한다. (HmacUtils.sign의 서명 대상과 동일한 직렬화 결과여야 한다)
     */
    private String objectMapperWriteValue(UserIdCard idCard) {
        try {
            return objectMapper.writeValueAsString(idCard);
        } catch (Exception e) {
            throw new IllegalStateException("UserIdCard 직렬화 실패", e);
        }
    }

    /**
     * 인증 불필요 경로 (로그인 / 회원가입 / 크리에이터 가입). 가입은 POST만 허용한다.
     *
     * NOTE(임시): 회원가입 경로가 /api/v1/members, /api/v1/creators 로 분리되어 있어 그대로 화이트리스트에 둔다.
     * 추후 user-service 경로를 /api/v1/users/** 로 통일하는 리팩터링 시 함께 정리한다.
     */
    private boolean isPermitAll(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (path.equals("/api/v1/auth/login")) {
            return true;
        }
        boolean isSignUp = path.equals("/api/v1/members") || path.equals("/api/v1/creators");
        return isSignUp && HttpMethod.POST.equals(method);
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
        // 위조 헤더 제거(HIGHEST_PRECEDENCE) 다음에 실행
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
