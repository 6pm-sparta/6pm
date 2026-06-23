package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByCreatorId(UUID creatorId);
}
