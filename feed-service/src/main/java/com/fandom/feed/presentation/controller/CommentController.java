package com.fandom.feed.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.CommentService;
import com.fandom.feed.global.annotation.RequireRole;
import com.fandom.feed.global.constant.UserRole;
import com.fandom.feed.presentation.dto.request.CommentRequest;
import com.fandom.feed.presentation.dto.response.CommentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequestMapping("/api/v1/feeds")
@RestController
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @RequireRole({UserRole.MEMBER, UserRole.CREATOR})
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse.Create> createComment(
            @PathVariable UUID postId,
            @RequestBody @Valid CommentRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        CommentResponse.Create response = commentService.createComment(postId, request.content(), idCard.getUserId());
        return ApiResponse.created(response);
    }

    @RequireRole({UserRole.MEMBER, UserRole.CREATOR})
    @PostMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<CommentResponse.Update> updateComment(
            @PathVariable UUID commentId,
            @RequestBody @Valid CommentRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        CommentResponse.Update response = commentService.updateComment(commentId, request.content(), idCard.getUserId());
        return ApiResponse.success(response);
    }

    @PostMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<CommentResponse.Delete> deleteComment(
            @PathVariable UUID commentId,
            @CurrentIdCard UserIdCard idCard
    ) {
        CommentResponse.Delete response = commentService.deleteComment(commentId, idCard.getUserId(), idCard.isMaster());
        return ApiResponse.success(response);
    }
}