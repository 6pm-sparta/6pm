package com.fandom.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HmacUtils 단위 테스트.
 *
 * UserIdCard 위·변조 방지의 핵심. Gateway가 sign 한 서명을 downstream이 verify 했을 때,
 * payload나 서명이 조금이라도 바뀌면 검증이 실패해야 한다(무결성). 또한 서버 간 secretKey가
 * 다르면 검증이 실패해야 한다(키 불일치 탐지).
 */
@DisplayName("HmacUtils 단위 테스트")
class HmacUtilsTest {

    private static final String SECRET = "test-hmac-secret-key-at-least-32-bytes-long!!";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HmacUtils hmacUtils = new HmacUtils(SECRET, objectMapper);

    private String toJson(UserIdCard idCard) {
        try {
            return objectMapper.writeValueAsString(idCard);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("sign 으로 만든 서명은 동일 payload 에 대해 verify=true (왕복)")
    void sign_then_verify_success() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        String json = toJson(idCard);

        String signature = hmacUtils.sign(idCard);

        assertThat(hmacUtils.verify(json, signature)).isTrue();
    }

    @Test
    @DisplayName("payload(JSON)가 변조되면 같은 서명으로 verify=false")
    void verify_fails_when_payload_tampered() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        String signature = hmacUtils.sign(idCard);

        // 권한을 MASTER 로 위조한 payload
        String tamperedJson = toJson(UserIdCard.of(idCard.getUserId(), "MASTER"));

        assertThat(hmacUtils.verify(tamperedJson, signature)).isFalse();
    }

    @Test
    @DisplayName("서명이 변조되면 verify=false")
    void verify_fails_when_signature_tampered() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        String json = toJson(idCard);

        assertThat(hmacUtils.verify(json, "not-a-valid-signature")).isFalse();
    }

    @Test
    @DisplayName("secretKey 가 다르면 verify=false (서버 간 키 불일치 탐지)")
    void verify_fails_when_secret_differs() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        String json = toJson(idCard);
        String signature = hmacUtils.sign(idCard);

        HmacUtils otherKeyUtils = new HmacUtils("a-completely-different-secret-key-value-here", objectMapper);

        assertThat(otherKeyUtils.verify(json, signature)).isFalse();
    }

    @Test
    @DisplayName("동일 입력에 대해 sign 은 결정적이다(같은 서명 반환)")
    void sign_isDeterministic() {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "CREATOR");

        assertThat(hmacUtils.sign(idCard)).isEqualTo(hmacUtils.sign(idCard));
    }

    @Test
    @DisplayName("deserialize: 정상 JSON 은 UserIdCard 로 복원된다")
    void deserialize_success() {
        UUID userId = UUID.randomUUID();
        String json = toJson(UserIdCard.of(userId, "CREATOR"));

        UserIdCard result = hmacUtils.deserialize(json);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRole()).isEqualTo("CREATOR");
    }

    @Test
    @DisplayName("deserialize: 깨진 JSON 은 IllegalArgumentException")
    void deserialize_invalidJson_throws() {
        assertThatThrownBy(() -> hmacUtils.deserialize("{not valid json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("verify: idCardJson 이 null 이면 안전하게 false (예외 전파 안 함)")
    void verify_nullJson_returnsFalse() {
        assertThat(hmacUtils.verify(null, "any-signature")).isFalse();
    }
}
