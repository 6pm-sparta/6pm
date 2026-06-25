package com.fandom.chat_service.infra.repository;

import com.fandom.chat_service.domain.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, UUID> {

    // 크리에이터
    List<ChatMessage> findByRoomIdOrderByIdDesc(UUID roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(UUID roomId, UUID cursor, Pageable pageable);

    // 팬 -> 처음
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId " +
            "AND (m.senderRole = com.fandom.chat_service.domain.entity.SenderRole.CREATOR OR m.senderId = :me) " +
            "ORDER BY m.id DESC")
    List<ChatMessage> findFanMessages(@Param("roomId") UUID roomId, @Param("me") UUID me, Pageable pageable);

    // 팬 -> 커서 이후
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId " +
            "AND (m.senderRole = com.fandom.chat_service.domain.entity.SenderRole.CREATOR OR m.senderId = :me) " +
            "AND m.id < :cursor " +
            "ORDER BY m.id DESC")
    List<ChatMessage> findFanMessagesAfter(@Param("roomId") UUID roomId, @Param("me") UUID me,
                                           @Param("cursor") UUID cursor, Pageable pageable);
}
