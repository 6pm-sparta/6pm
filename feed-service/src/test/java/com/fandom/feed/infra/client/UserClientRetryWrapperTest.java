package com.fandom.feed.infra.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserClientRetryWrapperTest {
    @Autowired
    private UserClientRetryWrapper userClientRetryWrapper;

    @MockitoBean
    private UserClient userClient;

    @Test
    @DisplayName("RetryableException 발생 시 max-attempts만큼 재시도")
    void retriesOnRetryableException() {
        // given
        UUID authorId = UUID.randomUUID();
        given(userClient.countFollowers(authorId)).willThrow(retryableException());

        // when
        assertThatThrownBy(() -> userClientRetryWrapper.countFollowers(authorId));

        // then
        verify(userClient, times(3)).countFollowers(authorId);
    }

    @Test
    @DisplayName("첫 시도 실패 후 재시도에서 성공하면 정상 값 반환")
    void succeedsAfterRetry() {
        // given
        UUID authorId = UUID.randomUUID();
        given(userClient.countFollowers(authorId)).willThrow(retryableException()).willReturn(ApiResponse.success(42L));

        // when
        long result = userClientRetryWrapper.countFollowers(authorId);

        // then
        assertThat(result).isEqualTo(42L);
        verify(userClient, times(2)).countFollowers(authorId);
    }

    @Test
    @DisplayName("FeignException.NotFound는 재시도 없이 즉시 예외 전달")
    void doesNotRetryOnNotFound() {
        // given
        UUID authorId = UUID.randomUUID();
        Request request = Request.create(
                Request.HttpMethod.GET, "/", Map.of(), null, StandardCharsets.UTF_8, null
        );
        FeignException.NotFound notFound = new FeignException.NotFound("not found", request, null, null);
        given(userClient.getUser(authorId)).willThrow(notFound);

        // when & then
        assertThatThrownBy(() -> userClientRetryWrapper.getUser(authorId)).isInstanceOf(CustomException.class);
        verify(userClient, times(1)).getUser(authorId);
    }

    private RetryableException retryableException() {
        Request dummyRequest = Request.create(
                Request.HttpMethod.GET, "/", Map.of(), null, StandardCharsets.UTF_8, null
        );
        return new RetryableException(500, "connection error", Request.HttpMethod.GET, (Long) null, dummyRequest);
    }
}