package com.fandom.common.auth.config;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.auth.resolver.CurrentIdCardArgumentResolver;
import com.fandom.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * common-auth 자동 설정 클래스.
 * HmacUtils, IdCardVerificationFilter, CurrentIdCardArgumentResolver를 빈으로 등록한다.
 * GlobalExceptionHandler를 Import해서 각 서비스에 자동 적용된다.
 * AutoConfiguration.imports에 등록되어 각 서비스에 자동 적용된다.
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class CommonAuthAutoConfiguration implements WebMvcConfigurer {

    @Value("${hmac.secret-key}")
    private String secretKey;

    @Bean
    public HmacUtils hmacUtils(ObjectMapper objectMapper) {
        return new HmacUtils(secretKey, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<IdCardVerificationFilter> idCardVerificationFilter(HmacUtils hmacUtils) {
        IdCardVerificationFilter filter = new IdCardVerificationFilter(hmacUtils);
        FilterRegistrationBean<IdCardVerificationFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentIdCardArgumentResolver());
    }
}
