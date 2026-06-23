package com.fandom.chat_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_rooms_creator_id", columnNames = "creator_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class ChatRoom extends BaseEntity {

    // 1크리에이터 1채팅방
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    // 생성 시 방이름이 크리에이터 이름
    @Column(nullable = false, length = 100)
    private String title;

    @Builder
    private ChatRoom(UUID creatorId, String title) {
        this.creatorId = creatorId;
        this.title = title;
    }
}
