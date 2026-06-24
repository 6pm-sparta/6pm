package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatMessage;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageJpaRepository jpaRepository;

    @Override
    public ChatMessage save(ChatMessage message) {
        return jpaRepository.save(message);
    }

    @Override
    public List<ChatMessage> findMessages(UUID roomId, UUID cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return cursor == null
                ? jpaRepository.findByRoomIdOrderByIdDesc(roomId, page)
                : jpaRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, page);
    }

    @Override
    public List<ChatMessage> findMessagesForFan(UUID roomId, UUID requesterId, UUID cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return cursor == null
                ? jpaRepository.findFanMessages(roomId, requesterId, page)
                : jpaRepository.findFanMessagesAfter(roomId, requesterId, cursor, page);
    }
}
