package com.fandom.cs_service.domain.repository;

import com.fandom.cs_service.domain.entity.CsDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CsDocumentRepository {

    CsDocument save(CsDocument document);

    Optional<CsDocument> findById(UUID id);

    Page<CsDocument> findAll(Pageable pageable);
}
