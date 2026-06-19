package com.fandom.gateway_service.config;

import com.fandom.common.auth.HmacUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway에서 UserIdCard 서명에 사용할 HmacUtils를 빈으로 등록한다.
 *
 * common의 HmacUtils는 (secretKey, objectMapper)를 받는 일반 클래스라 빈 등록이 필요하다.
 * hmac.secret-key는 모든 도메인 서비스와 동일한 값이어야 하며, Config Server에서 공유받는다.
 * (도메인 서비스 쪽은 common의 CommonAuthAutoConfiguration이 동일하게 HmacUtils를 등록한다)
 */
@Configuration
public class HmacConfig {

    @Bean
    public HmacUtils hmacUtils(@Value("${hmac.secret-key}") String secretKey, ObjectMapper objectMapper) {
        return new HmacUtils(secretKey, objectMapper);
    }
}
