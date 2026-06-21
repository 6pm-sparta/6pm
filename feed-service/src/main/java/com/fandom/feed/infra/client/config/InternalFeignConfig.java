package com.fandom.feed.infra.client.config;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

@RequiredArgsConstructor
public class InternalFeignConfig {
    private final HmacUtils hmacUtils;
    private final ObjectMapper objectMapper;

    public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String SYSTEM_ROLE = "MASTER";

    @Bean
    public RequestInterceptor internalAuthInterceptor() {
        return requestTemplate -> {
            UserIdCard systemIdCard = UserIdCard.of(SYSTEM_USER_ID, SYSTEM_ROLE);

            try {
                String idCardJson = objectMapper.writeValueAsString(systemIdCard);
                String signature = hmacUtils.sign(systemIdCard);

                requestTemplate.header("X-Id-Card", idCardJson);
                requestTemplate.header("X-Id-Card-Signature", signature);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("내부 통신용 인증 헤더 직렬화 실패", e);
            }
        };
    }
}