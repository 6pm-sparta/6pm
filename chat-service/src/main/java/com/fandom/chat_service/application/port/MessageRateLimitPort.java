package com.fandom.chat_service.application.port;

import java.util.UUID;

public interface MessageRateLimitPort {

    // 슬로우 모드
    boolean tryAcquireSlowMode(UUID roomId, UUID userId);

    // 도배 억제
    boolean isDuplicate(UUID roomId, UUID userId, String content);
}
