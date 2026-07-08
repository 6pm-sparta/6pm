package com.fandom.ticketing_service.queue.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

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
        UUID showId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Set<String> candidates = Set.of(userId1.toString(), userId2.toString());
        String queueKey = "waiting_queue:" + showId;

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
        UUID showId = UUID.randomUUID();
        String queueKey = "waiting_queue:" + showId;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.range(queueKey, 0, 49)).willReturn(Set.of());

        // when
        ReflectionTestUtils.invokeMethod(queueScheduler, "processShowQueue", showId);

        // then
        verify(purchaseTokenService, never()).issue(any(), any());
        verify(zSetOperations, never()).remove(eq(queueKey), any());
    }

    @Test
    @DisplayName("Redis에 존재하는 waiting_queue 키로부터 활성 공연 ID를 추출한다")
    void findActiveShowIds_extractsShowIdsFromKeys() {
        // given
        UUID showId1 = UUID.randomUUID();
        UUID showId2 = UUID.randomUUID();
        given(redisTemplate.keys("waiting_queue:*")).willReturn(Set.of("waiting_queue:" + showId1, "waiting_queue:" + showId2));

        // when
        Set<UUID> result = ReflectionTestUtils.invokeMethod(queueScheduler, "findActiveShowIds");

        // then
        org.assertj.core.api.Assertions.assertThat(result).containsExactlyInAnyOrder(showId1, showId2);
    }

    @Test
    @DisplayName("waiting_queue 키가 없으면 빈 Set을 반환한다")
    void findActiveShowIds_noKeys_returnsEmptySet() {
        // given
        given(redisTemplate.keys("waiting_queue:*")).willReturn(Set.of());

        // when
        Set<UUID> result = ReflectionTestUtils.invokeMethod(queueScheduler, "findActiveShowIds");

        // then
        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("분산락을 획득하면 대기열을 처리하고 락을 해제한다")
    void processQueue_lockAcquired_processesAndUnlocks() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:queue-scheduler")).willReturn(lock);
        given(lock.tryLock(0, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.keys("waiting_queue:*")).willReturn(Set.of());

        // when
        queueScheduler.processQueue();

        // then
        verify(lock).unlock();
    }

    @Test
    @DisplayName("다른 인스턴스가 이미 락을 잡고 있으면 이번 주기는 처리하지 않고 스킵한다")
    void processQueue_lockNotAcquired_skips() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:queue-scheduler")).willReturn(lock);
        given(lock.tryLock(0, TimeUnit.SECONDS)).willReturn(false);

        // when
        queueScheduler.processQueue();

        // then
        verify(redisTemplate, never()).keys(anyString());
        verify(lock, never()).unlock();
    }
}
