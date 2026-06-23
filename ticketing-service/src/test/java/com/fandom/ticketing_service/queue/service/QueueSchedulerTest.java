package com.fandom.ticketing_service.queue.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueScheduler 단위 테스트")
class QueueSchedulerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private QueueSseService queueSseService;

    @Mock
    private PurchaseTokenService purchaseTokenService;

    @InjectMocks
    private QueueScheduler queueScheduler;

    @Test
    @DisplayName("대기 중인 후보가 있으면 각각 구매 토큰을 발급하고 대기열에서 제거한다")
    void processShowQueue_withCandidates_issuesTokensAndRemoves() {
        // given
        Long showId = 1L;
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Set<String> candidates = Set.of(userId1.toString(), userId2.toString());
        String queueKey = "waiting_queue:1";

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.range(queueKey, 0, 49)).willReturn(candidates);
        given(purchaseTokenService.issue(eq(showId), any(UUID.class))).willReturn(true);

        // when
        ReflectionTestUtils.invokeMethod(queueScheduler, "processShowQueue", showId);

        // then
        verify(purchaseTokenService).issue(showId, userId1);
        verify(purchaseTokenService).issue(showId, userId2);
        verify(zSetOperations).remove(queueKey, candidates.toArray());
    }

    @Test
    @DisplayName("대기 중인 후보가 없으면 토큰 발급도, 제거도 하지 않는다")
    void processShowQueue_noCandidates_doesNothing() {
        // given
        Long showId = 1L;
        String queueKey = "waiting_queue:1";

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.range(queueKey, 0, 49)).willReturn(Set.of());

        // when
        ReflectionTestUtils.invokeMethod(queueScheduler, "processShowQueue", showId);

        // then
        verify(purchaseTokenService, never()).issue(any(), any());
        verify(zSetOperations, never()).remove(eq(queueKey), any());
    }
}
