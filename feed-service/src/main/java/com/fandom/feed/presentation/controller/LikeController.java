package com.fandom.feed.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.LikeService;
import com.fandom.feed.global.annotation.RequireRole;
import com.fandom.feed.global.constant.UserRole;
import com.fandom.feed.presentation.dto.response.LikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api/v1/feeds")
@RestController
@RequiredArgsConstructor
public class LikeController {
    private final LikeService likeService;

    @RequireRole({UserRole.MEMBER, UserRole.CREATOR})
    @PostMapping("/posts/{postId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LikeResponse> createLike(
            @PathVariable UUID postId,
            @CurrentIdCard UserIdCard idCard
    ) {
        LikeResponse response = likeService.createLike(postId, idCard.getUserId());
        return ApiResponse.created(response);
    }

    @RequireRole({UserRole.MEMBER, UserRole.CREATOR})
    @DeleteMapping("/posts/{postId}/likes")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LikeResponse> deleteLike(
            @PathVariable UUID postId,
            @CurrentIdCard UserIdCard idCard
    ) {
        LikeResponse response = likeService.deleteLike(postId, idCard.getUserId());
        return ApiResponse.success(response);
    }
}