package com.fandom.user_service.profile.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.profile.application.ProfileService;
import com.fandom.user_service.profile.presentation.dto.request.CreatorProfileUpdateRequest;
import com.fandom.user_service.profile.presentation.dto.request.MemberProfileUpdateRequest;
import com.fandom.user_service.profile.presentation.dto.response.CreatorProfileResponse;
import com.fandom.user_service.profile.presentation.dto.response.MemberProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/members/{memberId}/profile")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getMemberProfile(@PathVariable UUID memberId) {
        MemberProfileResponse response = profileService.getMemberProfile(memberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/creators/{creatorId}/profile")
    public ResponseEntity<ApiResponse<CreatorProfileResponse>> getCreatorProfile(@PathVariable UUID creatorId) {
        CreatorProfileResponse response = profileService.getCreatorProfile(creatorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/members/me/profile")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> updateMemberProfile(
            @CurrentIdCard UserIdCard idCard,
            @Valid @RequestBody MemberProfileUpdateRequest request
    ) {
        MemberProfileResponse response = profileService.updateMemberProfile(idCard.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/creators/me/profile")
    public ResponseEntity<ApiResponse<CreatorProfileResponse>> updateCreatorProfile(
            @CurrentIdCard UserIdCard idCard,
            @Valid @RequestBody CreatorProfileUpdateRequest request
    ) {
        CreatorProfileResponse response = profileService.updateCreatorProfile(idCard.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
