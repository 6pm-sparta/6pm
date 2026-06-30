package com.fandom.feed.infra.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.infra.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    @GetMapping("/internal/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);

    @PostMapping("/internal/v1/users")
    ApiResponse<List<UserResponse>> getUsers(@RequestBody Set<UUID> userIds);
}