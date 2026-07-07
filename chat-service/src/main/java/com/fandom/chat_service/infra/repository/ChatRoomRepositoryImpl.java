package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepository {

    private final ChatRoomJpaRepository jpaRepository;

    @Override
    public ChatRoom save(ChatRoom room) {
        return jpaRepository.save(room);
    }

    @Override
    public Optional<ChatRoom> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ChatRoom> findByCreatorId(UUID creatorId) {
        return jpaRepository.findByCreatorId(creatorId);
    }

    @Override
    public List<ChatRoom> findAllByIdIn(Collection<UUID> ids) {
        return jpaRepository.findAllByIdIn(ids);
    }
}
