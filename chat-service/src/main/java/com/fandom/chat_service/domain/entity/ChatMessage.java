package com.fandom.chat_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_room_id_id", columnList = "room_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class ChatMessage extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_role", nullable = false, length = 10)
    private String senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private ChatMessage(UUID roomId, UUID senderId, String senderRole, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
    }
}
