package com.fandom.cs_service.domain.entity;

import com.fandom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "cs_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class CsDocument extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private CsDocument(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
