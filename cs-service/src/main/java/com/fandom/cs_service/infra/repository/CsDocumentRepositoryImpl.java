package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsDocument;
import com.fandom.cs_service.domain.repository.CsDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CsDocumentRepositoryImpl implements CsDocumentRepository {

    private final CsDocumentJpaRepository jpaRepository;

    @Override
    public CsDocument save(CsDocument document) {
        return jpaRepository.save(document);
    }

    @Override
    public Optional<CsDocument> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<CsDocument> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }
}
