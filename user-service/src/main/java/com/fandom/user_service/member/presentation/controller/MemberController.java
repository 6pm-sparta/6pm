package com.fandom.user_service.member.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.member.application.MemberService;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.SignUpResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 가입 API. (외부 - Gateway 경유)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MemberController {

    private final MemberService memberService;

    /**
     * 일반회원 가입.
     */
    @PostMapping("/members")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResponse response = memberService.signUp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 크리에이터 가입.
     */
    @PostMapping("/creators")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUpCreator(@Valid @RequestBody CreatorSignUpRequest request) {
        SignUpResponse response = memberService.signUpCreator(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }
}
