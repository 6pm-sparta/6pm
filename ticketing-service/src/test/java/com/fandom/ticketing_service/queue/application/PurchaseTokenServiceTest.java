package com.fandom.ticketing_service.queue.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseTokenService 단위 테스트")
class PurchaseTokenServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PurchaseTokenService purchaseTokenService;

    @Test
    @DisplayName("토큰이 없으면 발급에 성공하고 true를 반환한다")
    void issue_notExists_returnsTrue() {
        // given
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String expectedKey = "purchase-token:" + showId + ":" + userId;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), any(Duration.class))).willReturn(true);

        // when
        boolean result = purchaseTokenService.issue(showId, userId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("토큰이 이미 있으면 발급에 실패하고 false를 반환한다")
    void issue_alreadyExists_returnsFalse() {
        // given
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).willReturn(false);

        // when
        boolean result = purchaseTokenService.issue(showId, userId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("토큰이 존재하면 exists는 true를 반환한다")
    void exists_keyPresent_returnsTrue() {
        // given
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String expectedKey = "purchase-token:" + showId + ":" + userId;

        given(redisTemplate.hasKey(expectedKey)).willReturn(true);

        // when
        boolean result = purchaseTokenService.exists(showId, userId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("토큰이 없으면 exists는 false를 반환한다")
    void exists_keyAbsent_returnsFalse() {
        // given
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(redisTemplate.hasKey(any())).willReturn(false);

        // when
        boolean result = purchaseTokenService.exists(showId, userId);

        // then
        assertThat(result).isFalse();
    }
}
