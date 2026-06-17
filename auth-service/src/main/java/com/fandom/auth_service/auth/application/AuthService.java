package com.fandom.auth_service.auth.application;

import com.fandom.auth_service.auth.domain.exception.AuthErrorCode;
import com.fandom.auth_service.auth.infrastructure.client.MemberLookupClient;
import com.fandom.auth_service.auth.infrastructure.client.dto.MemberLookupResponse;
import com.fandom.auth_service.auth.infrastructure.jwt.JwtProvider;
import com.fandom.auth_service.auth.presentation.dto.request.LoginRequest;
import com.fandom.auth_service.auth.presentation.dto.response.LoginResponse;
import com.fandom.common.exception.CustomException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 처리 서비스.
 * user-service 내부 조회로 회원을 확인하고, 비밀번호를 검증한 뒤 Access Token을 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemberLookupClient memberLookupClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest request) {
        MemberLookupResponse member = lookupMember(request.email());

        // 비밀번호 검증 (불일치 시 계정 존재 여부 노출 방지를 위해 동일 에러)
        if (!passwordEncoder.matches(request.password(), member.password())) {
            throw new CustomException(AuthErrorCode.LOGIN_FAILED);
        }

        // 계정 상태 확인
        if (!STATUS_ACTIVE.equals(member.status())) {
            throw new CustomException(AuthErrorCode.INACTIVE_MEMBER);
        }

        String accessToken = jwtProvider.createAccessToken(member.userId(), member.role());
        return LoginResponse.of(accessToken, jwtProvider.getAccessTokenExpiration());
    }

    /**
     * user-service에서 회원 조회. 회원이 없으면(404) 로그인 실패로 변환한다.
     * (계정 존재 여부가 노출되지 않도록 비밀번호 불일치와 동일한 에러로 처리)
     */
    private MemberLookupResponse lookupMember(String email) {
        try {
            return memberLookupClient.getMemberByEmail(email).getData();
        } catch (FeignException.NotFound e) {
            throw new CustomException(AuthErrorCode.LOGIN_FAILED);
        } catch (FeignException e) {
            log.error("[AuthService] 회원 조회 실패 - status: {}, message: {}", e.status(), e.getMessage());
            throw new CustomException(AuthErrorCode.MEMBER_LOOKUP_FAILED);
        }
    }
}
