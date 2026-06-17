package com.fandom.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 검증용 인코더 빈.
 * 로그인 시 입력 비밀번호와 user-service에서 받은 BCrypt 해시를 matches로 비교한다.
 * (필터 체인 없이 인코더만 사용. 인증 검증은 Gateway 담당.)
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
