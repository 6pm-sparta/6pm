package com.fandom.ticketing_service.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final String WAITING_QUEUE_KEY = "waiting_queue:%s";
    private static final String WAITING_QUEUE_PATTERN = "waiting_queue:*";
    private static final int BATCH_SIZE = 50;

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueSseService queueSseService;
    private final PurchaseTokenService purchaseTokenService;

    // 대기열이 실제로 존재하는 공연만 처리 대상으로 삼는다 (대기열 키는 enter() 시점에 생성되고, 비워지면 자동으로 사라짐)
    // 기본 주기는 60초. Postman 등 빠른 테스트가 필요하면 환경변수 QUEUE_SCHEDULER_DELAY(ms)로 오버라이드 (예: QUEUE_SCHEDULER_DELAY=2000)
    @Scheduled(fixedDelayString = "${queue.scheduler.delay:60000}")
    public void processQueue() {
        for (UUID showId : findActiveShowIds()) {
            processShowQueue(showId);
            queueSseService.broadcastRank(showId);
        }
    }

    private Set<UUID> findActiveShowIds() {
        Set<String> keys = redisTemplate.keys(WAITING_QUEUE_PATTERN);
        if (keys == null || keys.isEmpty()) return Set.of();

        return keys.stream()
                .map(key -> UUID.fromString(key.substring(key.lastIndexOf(':') + 1)))
                .collect(Collectors.toSet());
    }

    private void processShowQueue(UUID showId) {
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
