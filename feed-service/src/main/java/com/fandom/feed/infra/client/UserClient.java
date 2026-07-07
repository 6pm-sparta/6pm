package com.fandom.feed.infra.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.infra.client.dto.FollowingResponse;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);

    @PostMapping("/internal/v1/users")
    ApiResponse<List<UserResponse>> getUsers(@RequestBody Set<UUID> userIds);

    @GetMapping("/internal/v1/follows/{authorId}/count")
    ApiResponse<Long> countFollowers(@PathVariable UUID authorId);

    @GetMapping("/internal/v1/follows/{authorId}/followers")
    ApiResponse<CursorPageResponse<UUID>> getFollowerIds(
            @PathVariable UUID authorId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size
    );

    @GetMapping("/internal/v1/follows/{userId}/followings")
    ApiResponse<CursorPageResponse<FollowingResponse>> getFollowingIds(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size,
            @RequestParam long minFollowerCount
    );

    @GetMapping("/internal/v1/follows/{userId}/followings/large")
    ApiResponse<CursorPageResponse<UUID>> getLargeFollowingIds(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size,
            @RequestParam long minFollowerCount
    );
}