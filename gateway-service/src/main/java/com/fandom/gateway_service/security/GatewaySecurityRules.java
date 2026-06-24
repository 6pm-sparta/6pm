package com.fandom.gateway_service.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class GatewaySecurityRules {

    private static final String MEMBER = "MEMBER";
    private static final String CREATOR = "CREATOR";
    private static final String MASTER = "MASTER";

    private static final Set<String> ALL_AUTHENTICATED = Set.of(MEMBER, CREATOR, MASTER);
    private static final Set<String> MEMBER_ONLY = Set.of(MEMBER);
    private static final Set<String> CREATOR_ONLY = Set.of(CREATOR);
    private static final Set<String> MEMBER_OR_CREATOR = Set.of(MEMBER, CREATOR);
    private static final Set<String> CREATOR_OR_MASTER = Set.of(CREATOR, MASTER);

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

    public boolean isAllowed(ServerHttpRequest request, String role) {
        if (isPermitAll(request)) {
            return true;
        }
        return requiredRoles(request).contains(role);
    }

    private Set<String> requiredRoles(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (matches(method, HttpMethod.PATCH, path, "^/api/v1/members/me$")) {
            return MEMBER_ONLY;
        }
        if (matches(method, HttpMethod.PATCH, path, "^/api/v1/members/me/profile$")) {
            return MEMBER_ONLY;
        }
        if (matches(method, HttpMethod.DELETE, path, "^/api/v1/members/me$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.PATCH, path, "^/api/v1/creators/me$")) {
            return CREATOR_ONLY;
        }
        if (matches(method, HttpMethod.PATCH, path, "^/api/v1/creators/me/profile$")) {
            return CREATOR_ONLY;
        }
        if (matches(method, HttpMethod.POST, path, "^/api/v1/follows/[^/]+$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.DELETE, path, "^/api/v1/follows/[^/]+$")) {
            return MEMBER_OR_CREATOR;
        }

        if (matches(method, HttpMethod.POST, path, "^/api/v1/feeds/posts$")) {
            return CREATOR_ONLY;
        }
        if (matches(method, HttpMethod.PUT, path, "^/api/v1/feeds/posts/[^/]+$")) {
            return CREATOR_ONLY;
        }
        if (matches(method, HttpMethod.DELETE, path, "^/api/v1/feeds/posts/[^/]+$")) {
            return CREATOR_OR_MASTER;
        }
        if (matches(method, HttpMethod.POST, path, "^/api/v1/feeds/posts/[^/]+/comments$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.PUT, path, "^/api/v1/feeds/comments/[^/]+$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.POST, path, "^/api/v1/feeds/posts/[^/]+/likes$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.DELETE, path, "^/api/v1/feeds/posts/[^/]+/likes$")) {
            return MEMBER_OR_CREATOR;
        }
        if (matches(method, HttpMethod.POST, path, "^/api/v1/feeds/likes/users$")) {
            return MEMBER_OR_CREATOR;
        }

        return ALL_AUTHENTICATED;
    }

    private boolean matches(HttpMethod actualMethod, HttpMethod expectedMethod, String path, String regex) {
        return expectedMethod.equals(actualMethod) && path.matches(regex);
    }
}
