package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.MessageRateLimitPort;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.common.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MessagePolicy {

    private final MessageRateLimitPort rateLimit;
    private final int maxLength;

    public MessagePolicy(MessageRateLimitPort rateLimit,
                         @Value("${chat.message-control.max-length:500}") int maxLength) {
        this.rateLimit = rateLimit;
        this.maxLength = maxLength;
    }

    public void check(ChatRoom room, UUID senderId, String content) {
        // 길이 제한
        if (content != null && content.length() > maxLength) {
            throw new CustomException(ChatErrorCode.CHAT_MESSAGE_TOO_LONG);
        }

        // CREATOR 예외
        if (room.getCreatorId().equals(senderId)) {
            return;
        }

        UUID roomId = room.getId();

        // 슬로우 모드
        if (!rateLimit.tryAcquireSlowMode(roomId, senderId)) {
            throw new CustomException(ChatErrorCode.CHAT_SLOW_MODE);
        }
        // 도배 억제
        if (rateLimit.isDuplicate(roomId, senderId, content)) {
            throw new CustomException(ChatErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
    }
}
