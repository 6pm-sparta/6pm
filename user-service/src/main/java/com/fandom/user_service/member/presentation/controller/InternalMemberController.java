package com.fandom.user_service.member.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.member.application.MemberService;
import com.fandom.user_service.member.presentation.dto.response.InternalMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 회원 조회 API. (서비스 간 통신 전용 - Gateway가 수신하지 않아야 함)
 * Auth Service의 로그인 검증 등에 사용된다. 비밀번호 해시를 포함한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalMemberController {

    private final MemberService memberService;

    /**
     * email로 회원 정보 조회. (로그인 검증용)
     */
    @GetMapping("/members/{email}")
    public ResponseEntity<ApiResponse<InternalMemberResponse>> getMemberByEmail(@PathVariable String email) {
        InternalMemberResponse response = memberService.findByEmailForInternal(email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
