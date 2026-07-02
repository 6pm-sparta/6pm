package com.fandom.cs_service.domain.repository;

import com.fandom.cs_service.domain.entity.CsMessage;

import java.util.List;
import java.util.UUID;

public interface CsMessageRepository {

    CsMessage save(CsMessage message);

    List<CsMessage> findMessages(UUID userId, UUID cursor, int limit);

    void softDeleteAllByUserId(UUID userId);
}
