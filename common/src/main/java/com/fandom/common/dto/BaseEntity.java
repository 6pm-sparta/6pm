//package com.fandom.common.dto;
//
//import com.fasterxml.uuid.Generators;
//import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
//import jakarta.persistence.*;
//import lombok.Getter;
//import org.springframework.data.annotation.CreatedBy;
//import org.springframework.data.annotation.CreatedDate;
//import org.springframework.data.annotation.LastModifiedBy;
//import org.springframework.data.annotation.LastModifiedDate;
//import org.springframework.data.jpa.domain.support.AuditingEntityListener;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Getter
//@MappedSuperclass
//@EntityListeners(AuditingEntityListener.class)
//public abstract class BaseEntity {
//
//    private static final TimeBasedEpochGenerator UUID_GENERATOR =
//            Generators.timeBasedEpochGenerator();
//
//    @Id
//    @Column(name = "id", updatable = false, nullable = false)
//    private UUID id;
//
//    @CreatedDate
//    @Column(name = "created_at", updatable = false, nullable = false)
//    private LocalDateTime createdAt;
//
//    @CreatedBy
//    @Column(name = "created_by", updatable = false)
//    private UUID createdBy;
//
//    @LastModifiedDate
//    @Column(name = "updated_at", nullable = false)
//    private LocalDateTime updatedAt;
//
//    @LastModifiedBy
//    @Column(name = "updated_by")
//    private UUID updatedBy;
//
//    @Column(name = "deleted_at")
//    private LocalDateTime deletedAt;
//
//    @Column(name = "deleted_by")
//    private UUID deletedBy;
//
//    // 다른 서비스에 id를 save 전에 넘겨야 할 때 사용. id가 이미 있으면 무시.
//    protected void assignId(UUID id) {
//        if (this.id == null) {
//            this.id = id;
//        }
//    }
//
//    @PrePersist
//    protected void prePersist() {
//        if (id == null) {
//            id = UUID_GENERATOR.generate();
//        }
//    }
//
//    public void softDelete(UUID deletedBy) {
//        if (isDeleted()) return;
//        this.deletedAt = LocalDateTime.now();
//        this.deletedBy = deletedBy;
//    }
//
//    public boolean isDeleted() {
//        return this.deletedAt != null;
//    }
//}