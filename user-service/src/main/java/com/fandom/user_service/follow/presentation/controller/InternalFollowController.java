package com.fandom.user_service.follow.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.follow.application.FollowService;
import com.fandom.user_service.follow.presentation.dto.response.CursorPageResponse;
import com.fandom.user_service.follow.presentation.dto.response.InternalFollowingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 내부 팔로우 조회 API. (서비스 간 통신 전용 - Gateway가 수신하지 않아야 함)
 * Feed 서비스의 피드 팬아웃/알람 발행에 사용된다. 프로필을 제외하고 ID만 반환한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/follows")
public class InternalFollowController {

    private final FollowService followService;

    /**
     * 팔로워 수 조회. (팬아웃 여부 판단용)
     */
    @GetMapping("/{authorId}/count")
    public ResponseEntity<ApiResponse<Long>> countFollowers(@PathVariable UUID authorId) {
        long count = followService.countFollowers(authorId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * 팔로워 ID 목록 조회. (팬아웃 대상 조회용, 커서 페이징)
     */
    @GetMapping("/{authorId}/followers")
    public ResponseEntity<ApiResponse<CursorPageResponse<UUID>>> getFollowerIds(
            @PathVariable UUID authorId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size
    ) {
        CursorPageResponse<UUID> response = followService.getFollowerIds(authorId, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 팔로잉 ID 목록 조회. (타임라인 구성용, 커서 페이징)
     * 각 대상의 대형 크리에이터 여부(isLarge = followerCount > minFollowerCount)를 함께 반환한다.
     */
    @GetMapping("/{userId}/followings")
    public ResponseEntity<ApiResponse<CursorPageResponse<InternalFollowingResponse>>> getFollowingIds(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size,
            @RequestParam long minFollowerCount
    ) {
        CursorPageResponse<InternalFollowingResponse> response =
                followService.getFollowingIds(userId, cursor, size, minFollowerCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 팔로잉 중 대형 크리에이터만 조회. (대형 대상은 pull 전략 등 별도 처리가 필요해 분리 제공)
     * authorId(UUID) 목록만 반환한다.
     */
    @GetMapping("/{userId}/followings/large")
    public ResponseEntity<ApiResponse<CursorPageResponse<UUID>>> getLargeFollowingIds(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam int size,
            @RequestParam long minFollowerCount
    ) {
        CursorPageResponse<UUID> response =
                followService.getLargeFollowingIds(userId, cursor, size, minFollowerCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
