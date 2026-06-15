package com.fandom.common.auth.filter;

import com.fandom.common.auth.HmacUtils;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * X-Id-Card / X-Id-Card-Signature 헤더를 검증하는 필터.
 * 검증 성공 시 UserIdCard를 request attribute에 저장한다.
 * 헤더가 없으면 인증 불필요 API로 판단하고 통과시킨다. (경로별 접근 제어는 SecurityConfig 책임)
 */
@Slf4j
@RequiredArgsConstructor
public class IdCardVerificationFilter extends OncePerRequestFilter {

    public static final String ID_CARD_HEADER = "X-Id-Card";
    public static final String ID_CARD_SIGNATURE_HEADER = "X-Id-Card-Signature";
    public static final String ID_CARD_ATTRIBUTE = "userIdCard";

    private final HmacUtils hmacUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String idCardJson = request.getHeader(ID_CARD_HEADER);
        String signature = request.getHeader(ID_CARD_SIGNATURE_HEADER);

        // 헤더 없으면 인증 불필요 API로 판단하고 통과
        if (idCardJson == null || signature == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // HMAC 검증
        if (!hmacUtils.verify(idCardJson, signature)) {
            log.warn("[IdCardVerificationFilter] HMAC 검증 실패 - uri: {}", request.getRequestURI());
            throw new CustomException(CommonErrorCode.INVALID_ID_CARD);
        }

        // 검증 성공 시 UserIdCard를 request attribute에 저장
        UserIdCard idCard = hmacUtils.deserialize(idCardJson);
        request.setAttribute(ID_CARD_ATTRIBUTE, idCard);
        log.debug("[IdCardVerificationFilter] 검증 성공 - userId: {}, role: {}", idCard.getUserId(), idCard.getRole());

        filterChain.doFilter(request, response);
    }
}
