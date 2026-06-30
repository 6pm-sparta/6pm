package com.fandom.feed.infra.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.client.exception.UserErrorCode;
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
            @Override
            public ApiResponse<UserResponse> getUser(UUID id) {
                if (cause instanceof FeignException feignException) {
                    if (feignException.status() == 404) {
                        log.warn("[UserClient] 조회 대상 사용자 없음 - ID: {}", id);
                        throw new CustomException(UserErrorCode.USER_NOT_FOUND);
                    }
                }

                log.error("[UserClient] 조회 예외 발생 - ID: {}, 원인: {}", id, cause.getMessage());
                throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }

            @Override
            public ApiResponse<List<UserResponse>> getUsers(Set<UUID> ids) {
                String message = (cause != null && cause.getMessage() != null) ? cause.getMessage() : "Unknown";
                log.error("[UserClient] 목록 조회 실패로 인한 폴백 실행 - ID: {}, 원인: {}", ids, message);
                List<UserResponse> responses = ids.stream().map(id -> new UserResponse(id, "-")).toList();
                return ApiResponse.success(responses);
            }
        };
    }
}