package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.MessageRateLimitPort;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.common.exception.CustomException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MessagePolicy {

    private static final String BLOCKED_METRIC = "chat.message.blocked"; // {reason=length|slow|dup}

    private final MessageRateLimitPort rateLimit;
    private final MeterRegistry meterRegistry;
    private final int maxLength;

    public MessagePolicy(MessageRateLimitPort rateLimit,
                         MeterRegistry meterRegistry,
                         @Value("${chat.message-control.max-length:500}") int maxLength) {
        this.rateLimit = rateLimit;
        this.meterRegistry = meterRegistry;
        this.maxLength = maxLength;
    }

    public void check(ChatRoom room, UUID senderId, String content) {
        // 길이 제한
        if (content != null && content.length() > maxLength) {
            meterRegistry.counter(BLOCKED_METRIC, "reason", "length").increment();
            throw new CustomException(ChatErrorCode.CHAT_MESSAGE_TOO_LONG);
        }

        // CREATOR 예외
        if (room.getCreatorId().equals(senderId)) {
            return;
        }

        UUID roomId = room.getId();

        // 슬로우 모드
        if (!rateLimit.tryAcquireSlowMode(roomId, senderId)) {
            meterRegistry.counter(BLOCKED_METRIC, "reason", "slow").increment();
            throw new CustomException(ChatErrorCode.CHAT_SLOW_MODE);
        }
        // 도배 억제
        if (rateLimit.isDuplicate(roomId, senderId, content)) {
            meterRegistry.counter(BLOCKED_METRIC, "reason", "dup").increment();
            throw new CustomException(ChatErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
    }
}
