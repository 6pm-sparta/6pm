package com.fandom.feed.infra.client;

import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserClientRetryWrapper {
    private final UserClient userClient;

    @Retry(name = "userClient")
    @CircuitBreaker(name = "userClient")
    public long countFollowers(UUID authorId) {
        return userClient.countFollowers(authorId).getData();
    }

    @Retry(name = "userClient")
    @CircuitBreaker(name = "userClient")
    public CursorPageResponse<UUID> getFollowerIds(UUID authorId, UUID cursor, int size) {
        return userClient.getFollowerIds(authorId, cursor, size).getData();
    }
}