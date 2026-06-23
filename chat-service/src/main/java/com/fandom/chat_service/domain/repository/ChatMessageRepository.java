package com.fandom.chat_service.domain.repository;

import com.fandom.chat_service.domain.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    // 방 메시지 이력 커서 조회
    List<ChatMessage> findMessages(UUID roomId, UUID cursor, int limit);
}
