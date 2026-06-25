package com.fandom.chat_service.domain.repository;

import com.fandom.chat_service.domain.entity.ChatRoom;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository {

    ChatRoom save(ChatRoom room);

    Optional<ChatRoom> findById(UUID id);

    // 크리에이터 방 조회
    Optional<ChatRoom> findByCreatorId(UUID creatorId);

    // 방 목록 배치 조회
    List<ChatRoom> findAllByIdIn(Collection<UUID> ids);
}
