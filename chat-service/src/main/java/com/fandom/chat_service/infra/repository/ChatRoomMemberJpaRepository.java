package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomMemberJpaRepository extends JpaRepository<ChatRoomMember, UUID> {

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<ChatRoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<ChatRoomMember> findAllByUserId(UUID userId);

    List<ChatRoomMember> findAllByRoomId(UUID roomId);

    @Query("SELECT m.userId FROM ChatRoomMember m WHERE m.roomId = :roomId")
    List<UUID> findUserIdsByRoomId(@Param("roomId") UUID roomId);

    void deleteByRoomIdAndUserId(UUID roomId, UUID userId);

    void deleteByRoomId(UUID roomId);

    void deleteByUserId(UUID userId);
}
