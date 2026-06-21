package com.fandom.feed.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.PostService;
import com.fandom.feed.global.annotation.RequireRole;
import com.fandom.feed.global.constant.UserRole;
import com.fandom.feed.presentation.dto.request.PostRequest;
import com.fandom.feed.application.policy.PostSort;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api/v1/feeds")
@RestController
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @RequireRole({UserRole.CREATOR})
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse.Create> createPost(
            @RequestBody @Valid PostRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        PostResponse.Create response = postService.createPost(request.content(), request.imageKeys(), idCard.getUserId());
        return ApiResponse.created(response);
    }

    @GetMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PostResponse.Detail> getPost(
            @PathVariable UUID postId,
            @CurrentIdCard UserIdCard idCard
    ) {
        UUID userId = extractLikeableUserId(idCard);
        PostResponse.Detail response = postService.getPost(postId, userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/posts")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<CursorPageResponse<PostResponse.Summary>> getPosts(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "LATEST") PostSort sort,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String keyword,
            @CurrentIdCard UserIdCard idCard
    ) {
        UUID userId = extractLikeableUserId(idCard);
        CursorPageResponse<PostResponse.Summary> response = postService.getPosts(cursor, sort, authorId, keyword, userId);
        return ApiResponse.success(response);
    }

    @RequireRole({UserRole.CREATOR})
    @PutMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PostResponse.Update> updatePost(
            @PathVariable UUID postId,
            @RequestBody @Valid PostRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        PostResponse.Update response = postService.updatePost(postId, request.content(), request.imageKeys(), idCard.getUserId());
        return ApiResponse.success(response);
    }

    @RequireRole({UserRole.CREATOR, UserRole.MASTER})
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PostResponse.Delete> deletePost(
            @PathVariable UUID postId,
            @CurrentIdCard UserIdCard idCard
    ) {
        PostResponse.Delete response = postService.deletePost(postId, idCard.getUserId(), idCard.isMaster());
        return ApiResponse.success(response);
    }

    /**
     * 좋아요 상태 조회가 필요한 경우에만 사용자 ID를 반환하는 메서드<br>
     * - Master 사용자인 경우, null 반환
     */
    private UUID extractLikeableUserId(UserIdCard idCard) {
        return idCard.isMaster() ? null : idCard.getUserId();
    }
}