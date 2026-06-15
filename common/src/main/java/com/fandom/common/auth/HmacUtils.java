package com.fandom.common.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * UserIdCard HMAC-SHA256 서명 및 검증 유틸리티.
 * Gateway에서 서명 생성, 각 도메인 서비스에서 검증에 사용된다.
 */
@Slf4j
@RequiredArgsConstructor
public class HmacUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secretKey;
    private final ObjectMapper objectMapper;

    // Gateway: JSON 직렬화 후 HMAC-SHA256 서명 생성
    public String sign(UserIdCard idCard) {
        try {
            String payload = objectMapper.writeValueAsString(idCard);
            return hmac(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserIdCard 직렬화 실패", e);
        }
    }

    // Domain Services: 검증(성공 시 true, 실패 시 false)
    public boolean verify(String idCardJson, String signature) {
        try {
            String expected = hmac(idCardJson);
            return expected.equals(signature);
        } catch (Exception e) {
            log.warn("[HmacUtils] 서명 검증 중 오류 발생: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JSON 문자열로부터 UserIdCard를 역직렬화한다.
     */
    public UserIdCard deserialize(String idCardJson) {
        try {
            return objectMapper.readValue(idCardJson, UserIdCard.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("UserIdCard 역직렬화 실패", e);
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 서명 생성 실패", e);
        }
    }
}
