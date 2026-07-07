package com.fandom.auth_service.auth.presentation.controller;

import com.fandom.auth_service.auth.application.AuthService;
import com.fandom.auth_service.auth.presentation.dto.request.LoginRequest;
import com.fandom.auth_service.auth.presentation.dto.request.LogoutRequest;
import com.fandom.auth_service.auth.presentation.dto.request.ReissueRequest;
import com.fandom.auth_service.auth.presentation.dto.response.LoginResponse;
import com.fandom.auth_service.auth.presentation.dto.response.ReissueResponse;
import com.fandom.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. (외부 - Gateway 경유, 단 로그인은 인증 예외 경로)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인. 성공 시 Access Token을 발급한다.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        ReissueResponse response = authService.reissue(request.refreshToken());
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(authorizationHeader, request.refreshToken());
        return ApiResponse.success();
    }
}
