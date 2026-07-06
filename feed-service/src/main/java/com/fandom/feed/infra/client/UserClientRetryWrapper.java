package com.fandom.feed.infra.client;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.feed.infra.client.dto.FollowingResponse;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.client.exception.UserErrorCode;
import com.fandom.feed.infra.util.LogContext;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Map.entry;

@Component
@RequiredArgsConstructor
public class UserClientRetryWrapper {
    private final UserClient userClient;

    @Value("${broadcast.fanout-threshold}")
    private long fanoutThreshold;

    @Value("${broadcast.chunk-size}")
    private int chunkSize;

    @Retry(name = "userClient", fallbackMethod = "getUserFallback")
    @CircuitBreaker(name = "userClient")
    public UserResponse getUser(UUID userId) {
        return userClient.getUser(userId).getData();
    }

    private UserResponse getUserFallback(UUID userId, FeignException.NotFound e) {
        LogContext.warn("[UserClient] 조회 대상 사용자 없음", entry("userId", userId));
        throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }

    private UserResponse getUserFallback(UUID userId, Exception e) {
        LogContext.error(e, "[UserClient] 조회 예외 발생", entry("userId", userId));
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Retry(name = "userClient", fallbackMethod = "getUsersFallback")
    @CircuitBreaker(name = "userClient")
    public List<UserResponse> getUsers(Set<UUID> userIds) {
        return userClient.getUsers(userIds).getData();
    }

    private List<UserResponse> getUsersFallback(Set<UUID> userIds, Exception e) {
        LogContext.error(e, "[UserClient] 목록 조회 실패로 인한 폴백 실행", entry("userIds", userIds));
        return userIds.stream().map(id -> new UserResponse(id, "-")).toList();
    }

    @Retry(name = "userClient", fallbackMethod = "countFollowersFallback")
    @CircuitBreaker(name = "userClient")
    public long countFollowers(UUID authorId) {
        return userClient.countFollowers(authorId).getData();
    }

    private long countFollowersFallback(UUID authorId, Exception e) {
        LogContext.error(e, "[UserClient] 팔로워 수 조회 실패", entry("userId", authorId));
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Retry(name = "userClient", fallbackMethod = "getFollowerIdsFallback")
    @CircuitBreaker(name = "userClient")
    public CursorPageResponse<UUID> getFollowerIds(UUID authorId, UUID cursor) {
        return userClient.getFollowerIds(authorId, cursor, chunkSize).getData();
    }

    private CursorPageResponse<UUID> getFollowerIdsFallback(UUID authorId, UUID cursor, Exception e) {
        LogContext.error(e, "[UserClient] 팔로워 목록 조회 실패", entry("userId", authorId), entry("cursor", cursor));
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Retry(name = "userClient", fallbackMethod = "getFollowingIdsFallback")
    @CircuitBreaker(name = "userClient")
    public CursorPageResponse<FollowingResponse> getFollowingIds(UUID userId, UUID cursor) {
        return userClient.getFollowingIds(userId, cursor, chunkSize, fanoutThreshold).getData();
    }

    private CursorPageResponse<FollowingResponse> getFollowingIdsFallback(UUID userId, UUID cursor, Exception e) {
        LogContext.error(e, "[UserClient] 팔로잉 목록 조회 실패", entry("userId", userId), entry("cursor", cursor));
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Retry(name = "userClient", fallbackMethod = "getLargeFollowingIdsFallback")
    @CircuitBreaker(name = "userClient")
    public CursorPageResponse<UUID> getLargeFollowingIds(UUID userId, UUID cursor) {
        return userClient.getLargeFollowingIds(userId, cursor, chunkSize, fanoutThreshold).getData();
    }

    private CursorPageResponse<UUID> getLargeFollowingIdsFallback(UUID userId, UUID cursor, Exception e) {
        LogContext.error(e, "[UserClient] 대형 크리에이터 팔로잉 목록 조회 실패", entry("userId", userId), entry("cursor", cursor));
        throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
}