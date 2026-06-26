package com.fandom.chat_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_room_members_room_user", columnNames = {"room_id", "user_id"}),
        indexes = @Index(name = "idx_chat_room_members_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Builder
    private ChatRoomMember(UUID roomId, UUID userId, String nickname) {
        this.roomId = roomId;
        this.userId = userId;
        this.nickname = nickname;
    }
}
