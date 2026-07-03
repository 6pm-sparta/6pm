package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CsMessageJpaRepository extends JpaRepository<CsMessage, UUID> {

    // 커서
    List<CsMessage> findByUserIdOrderByIdDesc(UUID userId, Pageable pageable);

    List<CsMessage> findByUserIdAndIdLessThanOrderByIdDesc(UUID userId, UUID cursor, Pageable pageable);

    // 탈퇴 - 문의 내역 일괄 삭제
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CsMessage m SET m.deletedAt = :now, m.deletedBy = :userId " +
           "WHERE m.userId = :userId AND m.deletedAt IS NULL")
    void softDeleteAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
