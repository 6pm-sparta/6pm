package com.fandom.common.auth.filter;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * IdCardVerificationFilter 단위 테스트.
 *
 * downstream 도메인 서비스의 최종 방어선. X-Id-Card / X-Id-Card-Signature 헤더를 HMAC 검증하고,
 * 위·변조되었거나 payload(userId/role)가 비정상이면 INVALID_ID_CARD 로 차단한다.
 * 헤더가 없으면 인증 불필요 API로 보고 통과시킨다(경로 접근 제어는 SecurityConfig 책임).
 */
@DisplayName("IdCardVerificationFilter 단위 테스트")
class IdCardVerificationFilterTest {

    private static final String SECRET = "test-hmac-secret-key-at-least-32-bytes-long!!";
    private static final String ID_CARD_HEADER = "X-Id-Card";
    private static final String SIGNATURE_HEADER = "X-Id-Card-Signature";
    private static final String ID_CARD_ATTRIBUTE = "userIdCard";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HmacUtils hmacUtils = new HmacUtils(SECRET, objectMapper);
    private final IdCardVerificationFilter filter = new IdCardVerificationFilter(hmacUtils);

    private String toJson(UserIdCard idCard) {
        try {
            return objectMapper.writeValueAsString(idCard);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletRequest requestWith(String uri, String idCardJson, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (idCardJson != null) request.addHeader(ID_CARD_HEADER, idCardJson);
        if (signature != null) request.addHeader(SIGNATURE_HEADER, signature);
        return request;
    }

    @Test
    @DisplayName("헤더가 없으면 통과시킨다 (인증 불필요 API)")
    void noHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();              // 다음 필터로 진행
        assertThat(request.getAttribute(ID_CARD_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("유효한 서명이면 통과 + UserIdCard를 request attribute에 저장한다")
    void validSignature_passesAndStoresAttribute() throws Exception {
        UUID userId = UUID.randomUUID();
        UserIdCard idCard = UserIdCard.of(userId, "MEMBER");
        MockHttpServletRequest request = requestWith("/api/v1/users/me", toJson(idCard), hmacUtils.sign(idCard));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Object attr = request.getAttribute(ID_CARD_ATTRIBUTE);
        assertThat(attr).isInstanceOf(UserIdCard.class);
        assertThat(((UserIdCard) attr).getUserId()).isEqualTo(userId);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("서명이 변조되면 INVALID_ID_CARD 로 차단하고 다음 필터로 넘기지 않는다")
    void tamperedSignature_blocked() throws Exception {
        UserIdCard idCard = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        MockHttpServletRequest request = requestWith("/api/v1/users/me", toJson(idCard), "forged-signature");
        FilterChain chain = mock(FilterChain.class);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ID_CARD);

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("payload가 변조되면(권한 상승 시도) HMAC 불일치로 차단한다")
    void tamperedPayload_blocked() {
        UserIdCard original = UserIdCard.of(UUID.randomUUID(), "MEMBER");
        String signature = hmacUtils.sign(original);
        // MEMBER로 서명받은 뒤 MASTER로 위조한 payload
        String tamperedJson = toJson(UserIdCard.of(original.getUserId(), "MASTER"));
        MockHttpServletRequest request = requestWith("/api/v1/admin", tamperedJson, signature);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ID_CARD);
    }

    @Test
    @DisplayName("서명은 유효하나 userId가 null이면 INVALID_ID_CARD 로 차단한다")
    void validSignatureButNullUserId_blocked() {
        UserIdCard nullUserId = UserIdCard.of(null, "MEMBER");
        MockHttpServletRequest request = requestWith("/api/v1/users/me", toJson(nullUserId), hmacUtils.sign(nullUserId));

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ID_CARD);
    }

    @Test
    @DisplayName("서명은 유효하나 role이 null이면 INVALID_ID_CARD 로 차단한다")
    void validSignatureButNullRole_blocked() {
        UserIdCard nullRole = UserIdCard.of(UUID.randomUUID(), null);
        MockHttpServletRequest request = requestWith("/api/v1/users/me", toJson(nullRole), hmacUtils.sign(nullRole));

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ID_CARD);
    }

    @Test
    @DisplayName("서명은 유효하나 role이 공백이면 INVALID_ID_CARD 로 차단한다")
    void validSignatureButBlankRole_blocked() {
        UserIdCard blankRole = UserIdCard.of(UUID.randomUUID(), "  ");
        MockHttpServletRequest request = requestWith("/api/v1/users/me", toJson(blankRole), hmacUtils.sign(blankRole));

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_ID_CARD);
    }

    @Test
    @DisplayName("idCard 헤더만 있고 signature가 없으면 통과시킨다 (둘 다 있어야 검증)")
    void onlyIdCardHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = requestWith("/api/v1/users/me",
                "{\"userId\":\"x\",\"role\":\"MEMBER\"}", null);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(ID_CARD_ATTRIBUTE)).isNull();
    }
}
