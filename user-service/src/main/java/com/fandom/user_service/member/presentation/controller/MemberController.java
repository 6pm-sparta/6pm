package com.fandom.user_service.member.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.member.application.MemberService;
import com.fandom.user_service.member.presentation.dto.request.CreatorSignUpRequest;
import com.fandom.user_service.member.presentation.dto.request.CreatorUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.MemberUpdateRequest;
import com.fandom.user_service.member.presentation.dto.request.SignUpRequest;
import com.fandom.user_service.member.presentation.dto.response.CreatorSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.CreatorUpdateResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberSignUpResponse;
import com.fandom.user_service.member.presentation.dto.response.MemberUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<ApiResponse<MemberSignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        MemberSignUpResponse response = memberService.signUp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 크리에이터 가입.
     */
    @PostMapping("/creators")
    public ResponseEntity<ApiResponse<CreatorSignUpResponse>> signUpCreator(@Valid @RequestBody CreatorSignUpRequest request) {
        CreatorSignUpResponse response = memberService.signUpCreator(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * 일반회원 본인 계정 정보 수정.
     */
    @PatchMapping("/members/me")
    public ResponseEntity<ApiResponse<MemberUpdateResponse>> updateMember(
            @CurrentIdCard UserIdCard idCard,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        MemberUpdateResponse response = memberService.updateMember(idCard.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    /**
     * 크리에이터 본인 계정 정보 수정.
     */
    @PatchMapping("/creators/me")
    public ResponseEntity<ApiResponse<CreatorUpdateResponse>> updateCreator(
            @CurrentIdCard UserIdCard idCard,
            @Valid @RequestBody CreatorUpdateRequest request
    ) {
        CreatorUpdateResponse response = memberService.updateCreator(idCard.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}
