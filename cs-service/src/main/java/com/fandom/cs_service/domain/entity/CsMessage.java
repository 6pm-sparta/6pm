package com.fandom.cs_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "cs_messages",
        indexes = @Index(name = "idx_cs_messages_user_id_id", columnList = "user_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class CsMessage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 10)
    private SenderRole senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private CsMessage(UUID userId, SenderRole senderRole, String content) {
        this.userId = userId;
        this.senderRole = senderRole;
        this.content = content;
    }
}
