package com.fandom.common.config;

import com.fandom.common.auth.annotation.CurrentIdCard;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 9개 도메인 서비스 공통 Swagger(OpenAPI) 설정.
 *
 * 문서를 두 그룹으로 나눈다:
 * - public   : /api/v1/**    (Gateway가 실제로 라우팅하는 외부 공개 API) — Server=Gateway, Bearer 인증 스킴 적용
 * - internal : /internal/v1/** (서비스 간 통신 전용, Gateway 라우트 없음) — Server=자기 자신, 인증 스킴 없음
 *
 * "internal 그룹을 Gateway를 거치지 않고 자기 자신에게 직접 호출해볼 수 있다"는 점은 의도적으로 그대로 둔다.
 * Gateway 라우트가 없다는 것 자체가 내부 API의 유일한 방어선이라는 사실을 시연에서 그대로 보여주기 위함.
 * (CORS 미설정 상태를 유지 — 각 서비스 자기 포트에서 열었을 때만 internal 그룹 Execute가 성공하고,
 *  Gateway 페이지에서 실수로 internal을 호출하면 CORS로 차단되는 것도 의도된 동작이다.)
 *
 * springdoc-openapi-starter-webmvc-ui가 classpath에 없는 모듈(gateway/eureka-server/config-server)에는
 * 이 설정이 전혀 적용되지 않는다(@ConditionalOnClass).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GroupedOpenApi.class)
public class CommonOpenApiAutoConfiguration {

    @Value("${spring.application.name:service}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String selfPort;

    @Value("${gateway.base-url:http://localhost:8080}")
    private String gatewayBaseUrl;

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public GroupedOpenApi publicApiGroup() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName(applicationName + " (public, via Gateway)")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new io.swagger.v3.oas.models.info.Info()
                            .title(applicationName + " API")
                            .description(applicationName + " 외부 공개 API. 모든 요청은 Gateway를 경유한다.")
                            .version("v1"));

                    openApi.servers(List.of(
                            new Server().url(gatewayBaseUrl).description("Gateway (정상 경로)")
                    ));

                    openApi.components(
                            (openApi.getComponents() != null ? openApi.getComponents()
                                    : new io.swagger.v3.oas.models.Components())
                                    .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                            .type(SecurityScheme.Type.HTTP)
                                            .scheme("bearer")
                                            .bearerFormat("JWT")
                                            .description("로그인(/api/v1/auth/login) 응답의 accessToken을 그대로 입력"))
                    );
                    openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
                })
                .build();
    }

    @Bean
    public GroupedOpenApi internalApiGroup() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName(applicationName + " (internal, Gateway 우회)")
                .pathsToMatch("/internal/v1/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new io.swagger.v3.oas.models.info.Info()
                            .title(applicationName + " Internal API")
                            .description("서비스 간 통신 전용 API. Gateway 라우트가 없다는 것이 유일한 방어선이며, "
                                    + "이 페이지(자기 포트)에서 직접 호출하면 인증 없이 그대로 성공한다.")
                            .version("v1"));

                    openApi.servers(List.of(
                            new Server().url("http://localhost:" + selfPort).description("자기 자신 (Gateway 우회)")
                    ));
                    // 인증 스킴 없음 — 실제로 아무 보호도 없다는 사실을 그대로 반영
                })
                .build();
    }

    /**
     * @CurrentIdCard가 붙은 파라미터는 Swagger 문서에서 숨긴다. 이 값은 클라이언트가 직접
     * 채우는 게 아니라, JWT 검증 후 Gateway가 서명된 X-Id-Card 헤더로 주입하는 값이다.
     * 이 커스터마이저가 없으면 springdoc이 이걸 일반 쿼리 파라미터(userId, role 등)로
     * 문서화해버려서 혼란을 준다. 실제 동작에는 영향 없음.
     */
    @Bean
    public ParameterCustomizer currentIdCardParameterCustomizer() {
        return (parameterModel, methodParameter) ->
                methodParameter.hasParameterAnnotation(CurrentIdCard.class) ? null : parameterModel;
    }
}
