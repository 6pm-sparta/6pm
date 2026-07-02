package com.fandom.common.logging.config;

import com.fandom.common.logging.filter.AccessLogFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonLoggingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter(ObjectProvider<Tracer> tracerProvider) {
        FilterRegistrationBean<AccessLogFilter> registrationBean =
                new FilterRegistrationBean<>(new AccessLogFilter(tracerProvider));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registrationBean;
    }
}
