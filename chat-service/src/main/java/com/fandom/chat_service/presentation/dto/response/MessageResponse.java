package com.fandom.chat_service.presentation.dto.response;

import com.fandom.chat_service.domain.entity.ChatMessage;
import com.fandom.chat_service.domain.entity.SenderRole;

import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID senderId,
        SenderRole senderRole,
        String content
) {
    public static MessageResponse from(ChatMessage m) {
        return new MessageResponse(m.getId(), m.getSenderId(), m.getSenderRole(), m.getContent());
    }
}
