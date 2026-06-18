package com.fandom.auth_service;

import com.fandom.common.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Auth Service.
 * 로그인 및 토큰 발급을 담당한다. 자체 DB는 두지 않고,
 * 회원 정보는 user-service의 내부 조회 API를 OpenFeign으로 호출해 검증한다.
 *
 * common 모듈이 JPA 관련 설정(DataSource/JPA/JpaAuditing)을 가져오지만
 * auth-service는 DB·엔티티를 두지 않으므로 해당 자동구성을 모두 제외한다.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaAuditingConfig.class
})
@EnableFeignClients
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
