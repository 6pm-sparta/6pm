package com.fandom.feed.infra.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.client.exception.UserErrorCode;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            final String message = (cause != null && cause.getMessage() != null) ? cause.getMessage() : "Unknown";

            @Override
            public ApiResponse<UserResponse> getUser(UUID userId) {
                if (cause instanceof FeignException feignException) {
                    if (feignException.status() == 404) {
                        log.warn("[UserClient] 조회 대상 사용자 없음 - userId: {}", userId);
                        throw new CustomException(UserErrorCode.USER_NOT_FOUND);
                    }
                }

                log.error("[UserClient] 조회 예외 발생 - userId: {}, 원인: {}", userId, message);
                throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }

            @Override
            public ApiResponse<List<UserResponse>> getUsers(Set<UUID> UserIds) {
                log.error("[UserClient] 목록 조회 실패로 인한 폴백 실행 - userIds: {}, 원인: {}", UserIds, message);
                List<UserResponse> responses = UserIds.stream().map(id -> new UserResponse(id, "-")).toList();
                return ApiResponse.success(responses);
            }

            @Override
            public ApiResponse<Long> countFollowers(UUID authorId) {
                log.error("[UserClient] 팔로워 수 조회 실패 - authorId: {}, 원인: {}", authorId, message);
                throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }

            @Override
            public ApiResponse<CursorPageResponse<UUID>> getFollowerIds(UUID authorId, UUID cursor, int size) {
                log.error("[UserClient] 팔로워 목록 조회 실패 - authorId: {}, cursor: {}, 원인: {}", authorId, cursor, message);
                throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
        };
    }
}