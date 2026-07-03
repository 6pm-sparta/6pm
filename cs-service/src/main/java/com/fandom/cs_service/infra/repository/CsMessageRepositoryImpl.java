package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.repository.CsMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CsMessageRepositoryImpl implements CsMessageRepository {

    private final CsMessageJpaRepository jpaRepository;

    @Override
    public CsMessage save(CsMessage message) {
        return jpaRepository.save(message);
    }

    @Override
    public List<CsMessage> findMessages(UUID userId, UUID cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return cursor == null
                ? jpaRepository.findByUserIdOrderByIdDesc(userId, page)
                : jpaRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, page);
    }

    @Override
    public void softDeleteAllByUserId(UUID userId) {
        jpaRepository.softDeleteAllByUserId(userId, LocalDateTime.now());
    }
}
