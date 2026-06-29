package com.fandom.ticketing_service.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSseService {

    private static final String WAITING_QUEUE_KEY = "waiting_queue:%s";
    private static final long SSE_TIMEOUT = 10 * 60 * 1000L; // 10분

    private final RedisTemplate<String, String> redisTemplate;

    // showId:userId → emitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(UUID showId, UUID userId) {
        String emitterKey = emitterKey(showId, userId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.put(emitterKey, emitter);
        emitter.onCompletion(() -> emitters.remove(emitterKey));
        emitter.onTimeout(() -> emitters.remove(emitterKey));

        sendRank(showId, userId, emitter);
        return emitter;
    }

    // 스케줄러에서 호출 — 연결된 전체 유저에게 현재 순번 전송
    public void broadcastRank(UUID showId) {
        String queueKey = WAITING_QUEUE_KEY.formatted(showId);

        emitters.entrySet().stream()
                .filter(e -> e.getKey().startsWith(showId + ":"))
                .forEach(e -> {
                    UUID userId = UUID.fromString(e.getKey().split(":")[1]);
                    sendRank(showId, userId, e.getValue());
                });
    }

    private void sendRank(UUID showId, UUID userId, SseEmitter emitter) {
        String queueKey = WAITING_QUEUE_KEY.formatted(showId);
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());

        try {
            if (rank == null) {
                // 대기열에 없음 = 입장 가능
                emitter.send(SseEmitter.event().name("READY").data("입장 가능"));
                emitter.complete();
            } else {
                emitter.send(SseEmitter.event().name("RANK").data(rank + 1)); // 0-indexed → 1-indexed
            }
        } catch (IOException e) {
            log.warn("SSE 전송 실패: {}", userId);
            emitter.completeWithError(e);
        }
    }

    private String emitterKey(UUID showId, UUID userId) {
        return showId + ":" + userId;
    }
}
