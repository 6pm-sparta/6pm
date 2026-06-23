package com.fandom.chat_service.domain.repository;

import com.fandom.chat_service.domain.entity.ChatRoom;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository {

    ChatRoom save(ChatRoom room);

    Optional<ChatRoom> findById(UUID id);

    // 크리에이터 방 조회
    Optional<ChatRoom> findByCreatorId(UUID creatorId);
}
