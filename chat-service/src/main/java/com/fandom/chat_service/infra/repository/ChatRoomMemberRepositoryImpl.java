package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChatRoomMemberRepositoryImpl implements ChatRoomMemberRepository {

    private final ChatRoomMemberJpaRepository jpaRepository;

    @Override
    public ChatRoomMember save(ChatRoomMember member) {
        return jpaRepository.save(member);
    }

    @Override
    public boolean existsByRoomIdAndUserId(UUID roomId, UUID userId) {
        return jpaRepository.existsByRoomIdAndUserId(roomId, userId);
    }

    @Override
    public List<ChatRoomMember> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<ChatRoomMember> findAllByRoomId(UUID roomId) {
        return jpaRepository.findAllByRoomId(roomId);
    }

    @Override
    public void deleteByRoomIdAndUserId(UUID roomId, UUID userId) {
        jpaRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    @Override
    public void deleteByRoomId(UUID roomId) {
        jpaRepository.deleteByRoomId(roomId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
