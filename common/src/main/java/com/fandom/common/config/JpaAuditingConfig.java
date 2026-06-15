package com.fandom.common.config;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        // TODO(7단계): 인증 도입 후 SecurityContextHolder 에서 로그인 사용자 UUID 반환
        return Optional::empty;
    }
}
