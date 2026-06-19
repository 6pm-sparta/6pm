package com.fandom.gateway_service;

import com.fandom.common.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Gateway Service.
 * 라우팅 및 인증 파이프라인(토큰 검증 · UserIdCard 전파)의 진입점이다.
 *
 * common 모듈이 JPA 관련 설정(DataSource/JPA/JpaAuditing)을 가져오지만
 * gateway-service는 DB·엔티티를 두지 않으므로 해당 자동구성을 모두 제외한다.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaAuditingConfig.class
})
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
