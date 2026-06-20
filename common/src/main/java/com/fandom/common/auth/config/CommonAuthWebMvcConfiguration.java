package com.fandom.common.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.auth.resolver.CurrentIdCardArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class CommonAuthWebMvcConfiguration implements WebMvcConfigurer {

    @Value("${hmac.secret-key:${HMAC_SECRET_KEY:6pm-fandom-sns-hmac-shared-secret-key-must-be-at-least-32-bytes-long}}")
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
