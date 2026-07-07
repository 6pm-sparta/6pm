package com.fandom.common.config;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommonOpenApiAutoConfiguration의 ParameterCustomizer 로직에 대한 순수 단위 테스트.
 *
 * @CurrentIdCard가 붙은 컨트롤러 파라미터는 Gateway가 X-Id-Card 헤더로 주입하는 값이라
 * 클라이언트가 직접 채우는 게 아니다. springdoc 기본 동작대로 두면 이 파라미터가 일반
 * 쿼리 파라미터(userId, role 등)로 문서화되어 오해를 주므로, 문서에서 제거되는지 검증한다.
 * 스프링 컨텍스트 없이 ParameterCustomizer 함수 자체만 검증하는 순수 단위 테스트다.
 */
@DisplayName("CommonOpenApiAutoConfiguration.currentIdCardParameterCustomizer 단위 테스트")
class CommonOpenApiAutoConfigurationTest {

    private final ParameterCustomizer customizer =
            new CommonOpenApiAutoConfiguration().currentIdCardParameterCustomizer();

    @SuppressWarnings("unused")
    static class SampleController {
        void withCurrentIdCard(@CurrentIdCard UserIdCard idCard) {
        }

        void withPlainParameter(String orderId) {
        }
    }

    private MethodParameter parameterOf(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(methodName, paramTypes);
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("@CurrentIdCard가 붙은 파라미터는 문서에서 제거한다(null 반환)")
    void removesCurrentIdCardParameter() throws NoSuchMethodException {
        MethodParameter methodParameter = parameterOf("withCurrentIdCard", UserIdCard.class);
        Parameter parameterModel = new Parameter().name("idCard");

        Parameter result = customizer.customize(parameterModel, methodParameter);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("@CurrentIdCard가 없는 일반 파라미터는 그대로 둔다")
    void keepsOrdinaryParameter() throws NoSuchMethodException {
        MethodParameter methodParameter = parameterOf("withPlainParameter", String.class);
        Parameter parameterModel = new Parameter().name("orderId");

        Parameter result = customizer.customize(parameterModel, methodParameter);

        assertThat(result).isSameAs(parameterModel);
    }

    @Test
    @DisplayName("public/internal 그룹 빈이 각각 올바른 group 이름으로 생성된다")
    void groupsAreCreatedWithExpectedNames() {
        // 서버 URL 등 프로퍼티 의존 값은 컨트롤러가 있는 서비스 모듈의 통합 테스트에서 검증한다.
        // 여기서는 그룹 정의 자체가 예외 없이 생성되고 이름이 맞는지만 확인한다.
        CommonOpenApiAutoConfiguration config = new CommonOpenApiAutoConfiguration();

        assertThat(config.publicApiGroup()).isNotNull();
        assertThat(config.publicApiGroup().getGroup()).isEqualTo("public");
        assertThat(config.internalApiGroup()).isNotNull();
        assertThat(config.internalApiGroup().getGroup()).isEqualTo("internal");
    }

    @Test
    @DisplayName("customizer는 인자값(UUID 등)과 무관하게 어노테이션 존재 여부만으로 판단한다")
    void worksRegardlessOfArgumentValue() throws NoSuchMethodException {
        MethodParameter methodParameter = parameterOf("withCurrentIdCard", UserIdCard.class);
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        Parameter parameterModel = new Parameter().name("idCard").example(idCard);

        Parameter result = customizer.customize(parameterModel, methodParameter);

        assertThat(result).isNull();
    }
}
