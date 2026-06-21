package com.fandom.user_service.follow.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.follow.application.FollowService;
import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.presentation.dto.response.FollowResponse;
import com.fandom.user_service.follow.presentation.dto.response.FollowerResponse;
import com.fandom.user_service.follow.presentation.dto.response.FollowingResponse;
import com.fandom.user_service.follow.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/follows/{creatorId}")
    public ResponseEntity<ApiResponse<FollowResponse>> follow(
            @CurrentIdCard UserIdCard idCard,
            @PathVariable UUID creatorId
    ) {
        Follow follow = followService.follow(idCard.getUserId(), creatorId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(FollowResponse.from(follow)));
    }

    @DeleteMapping("/follows/{creatorId}")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @CurrentIdCard UserIdCard idCard,
            @PathVariable UUID creatorId
    ) {
        followService.unfollow(idCard.getUserId(), creatorId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/follows/{creatorId}/followers")
    public ResponseEntity<ApiResponse<PageResponse<FollowerResponse>>> getFollowers(
            @CurrentIdCard UserIdCard idCard,
            @PathVariable UUID creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<FollowerResponse> response = followService.getFollowers(creatorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/members/{memberId}/followings")
    public ResponseEntity<ApiResponse<PageResponse<FollowingResponse>>> getFollowings(
            @CurrentIdCard UserIdCard idCard,
            @PathVariable UUID memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<FollowingResponse> response = followService.getFollowings(memberId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
