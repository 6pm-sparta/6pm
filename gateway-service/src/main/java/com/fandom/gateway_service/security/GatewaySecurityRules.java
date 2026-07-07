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

        if (isSwaggerOrApiDocs(path)) {
            return true;
        }

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

    /**
     * Swagger UI / OpenAPI 문서 조회 경로는 인증 없이 통과시킨다. 문서 조회 자체는 보안 리스크가 없고,
     * discovery locator가 자동 생성하는 서비스별 프록시 경로(예: /user-service/v3/api-docs)까지
     * 패턴을 계속 늘리지 않아도 되도록 contains로 느슨하게 매칭한다.
     * 실제 API 호출(Execute)은 각 라우트의 정상 인증 정책을 그대로 따른다 — 여기서 막는 건
     * "문서/UI 리소스 조회"뿐이다.
     */
    private boolean isSwaggerOrApiDocs(String path) {
        return path.contains("/v3/api-docs") || path.contains("/swagger-ui");
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
