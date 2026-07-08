package com.fandom.auth_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * common의 CommonOpenApiAutoConfiguration이 실제 도메인 서비스(auth-service)에 붙었을 때
 * 의도한 대로 문서가 생성되는지 확인하는 통합 테스트.
 *
 * common 모듈 자체에는 @RestController가 없어 GroupedOpenApi가 매칭할 경로가 없으므로,
 * 이 검증은 실제 컨트롤러가 있는 서비스 모듈에서 해야 의미가 있다. auth-service는 컨트롤러
 * 개수가 적어(AuthController 하나) 가볍게 확인하기 좋은 서비스로 선택했다.
 *
 * 로컬 Redis/Kafka/Eureka/config-server가 떠 있는 상태에서 실행해야 한다(팀 기존
 * *ApplicationTests 클래스들과 동일한 전제).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("auth-service Swagger(OpenAPI) 문서 통합 테스트")
class AuthServiceOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("public 그룹 문서는 Gateway를 서버로 지정하고, Bearer 인증 스킴을 포함한다")
    void publicGroupPointsToGatewayAndHasBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:8080"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/users']").doesNotExist());
    }

    @Test
    @DisplayName("internal 그룹 문서는 자기 자신을 서버로 지정하고, 인증 스킴이 없다")
    void internalGroupPointsToSelfAndHasNoSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs/internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").doesNotExist());
    }

    @Test
    @DisplayName("login API에는 @CurrentIdCard 파라미터가 없으므로 애초에 파라미터 목록이 비어있거나 body만 있다")
    void loginEndpointHasNoIdCardQueryParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.parameters").doesNotExist());
    }
}
