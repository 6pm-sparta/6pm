package com.fandom.gateway_service.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class GatewaySecurityRules {

    /**
     * Access Token 검증 없이 통과할 경로.
     *
     * NOTE: 회원가입 경로가 /api/v1/members, /api/v1/creators로 분리되어 있어 현재 정책에 맞춰 둔다.
     * 추후 user-service 경로를 /api/v1/users/**로 통일하면 함께 정리한다.
     */
    public boolean isPermitAll(ServerHttpRequest request) {
        if (isPreflight(request)) {
            return true;
        }

        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (path.equals("/api/v1/auth/login")) {
            return true;
        }

        if (path.equals("/api/v1/auth/reissue") && HttpMethod.POST.equals(method)) {
            return true;
        }

        boolean isSignUp = path.equals("/api/v1/members") || path.equals("/api/v1/creators");
        boolean isProfileLookup = (path.matches("^/api/v1/members/[^/]+/profile$")
                || path.matches("^/api/v1/creators/[^/]+/profile$"))
                && HttpMethod.GET.equals(method);

        return (isSignUp && HttpMethod.POST.equals(method)) || isProfileLookup;
    }

    public boolean isPreflight(ServerHttpRequest request) {
        return HttpMethod.OPTIONS.equals(request.getMethod());
    }
}
