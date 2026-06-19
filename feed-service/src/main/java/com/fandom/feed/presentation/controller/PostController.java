package com.fandom.feed.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.PostService;
import com.fandom.feed.global.annotation.RequireRole;
import com.fandom.feed.presentation.dto.request.PostRequest;
import com.fandom.feed.presentation.dto.response.PostResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api/feeds")
@RestController
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @RequireRole({"CREATOR"})
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse.Create> createPost(
            @Valid PostRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        PostResponse.Create response = postService.createPost(request.content(), request.imageKeys(), idCard.getUserId());
        return ApiResponse.created(response);
    }

    @GetMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PostResponse.Detail> getPost(
            @PathVariable UUID id,
            @CurrentIdCard UserIdCard idCard
    ) {
        UUID userId = idCard.isMaster() ? null : idCard.getUserId();
        PostResponse.Detail response = postService.getPost(id, userId);
        return ApiResponse.success(response);
    }
}