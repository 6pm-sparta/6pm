package com.fandom.user_service.member.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.user_service.member.presentation.dto.response.InternalUserResponse;
import com.fandom.user_service.profile.application.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 내부 사용자 조회 API. (서비스 간 통신 전용 - Gateway가 수신하지 않아야 함)
 * feed-service 등이 게시글/댓글 작성자 정보를 조합할 때 Feign으로 호출한다. Client는 직접 호출하지 않는다.
 *
 * /internal/v1 경로는 gateway-service의 라우트 predicate에 걸리지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalUserController {

    private final ProfileService profileService;

    /**
     * userId로 사용자 단건 조회. 탈퇴/미존재 시 404.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getUserForInternal(userId)));
    }

    /**
     * userId 집합으로 사용자 배치 조회. 없거나 탈퇴한 userId는 결과에서 제외된다.
     */
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<List<InternalUserResponse>>> getUsers(@RequestBody Set<UUID> userIds) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getUsersForInternal(userIds)));
    }
}
