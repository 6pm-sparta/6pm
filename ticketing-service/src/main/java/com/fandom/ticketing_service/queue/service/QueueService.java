package com.fandom.ticketing_service.queue.service;

import com.fandom.ticketing_service.queue.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue:%d";

    private final RedisTemplate<String, String> redisTemplate;

    public boolean enter(Long showId, UUID userId) {
        String key = WAITING_QUEUE_KEY.formatted(showId);
        double score = System.currentTimeMillis();
        // NX 옵션: 이미 대기열에 있으면 추가하지 않음
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(key, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    public QueueStatusResponse getStatus(Long showId, UUID userId) {
        String key = WAITING_QUEUE_KEY.formatted(showId);
        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        return QueueStatusResponse.of(rank);
    }
}
