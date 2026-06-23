package com.fandom.ticketing_service.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final String WAITING_QUEUE_KEY = "waiting_queue:%d";
    private static final int BATCH_SIZE = 50;

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueSseService queueSseService;
    private final PurchaseTokenService purchaseTokenService;

    // TODO: 실제 운영 시 활성 공연 ID 목록을 DB 또는 캐시에서 조회하도록 변경
    private static final Set<Long> ACTIVE_SHOW_IDS = Set.of();

    @Scheduled(fixedDelay = 60_000)
    public void processQueue() {
        for (Long showId : ACTIVE_SHOW_IDS) {
            processShowQueue(showId);
            queueSseService.broadcastRank(showId);
        }
    }

    private void processShowQueue(Long showId) {
        String queueKey = WAITING_QUEUE_KEY.formatted(showId);

        Set<String> candidates = redisTemplate.opsForZSet().range(queueKey, 0, BATCH_SIZE - 1);
        if (candidates == null || candidates.isEmpty()) return;

        for (String userId : candidates) {
            if (purchaseTokenService.issue(showId, UUID.fromString(userId))) {
                log.info("purchase-token 발급: userId={}, showId={}", userId, showId);
            }
        }

        redisTemplate.opsForZSet().remove(queueKey, candidates.toArray());
    }
}
