package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsDocument;
import com.fandom.cs_service.domain.repository.CsDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CsDocumentRepositoryImpl implements CsDocumentRepository {

    private final CsDocumentJpaRepository jpaRepository;

    @Override
    public CsDocument save(CsDocument document) {
        return jpaRepository.save(document);
    }

    @Override
    public List<CsDocument> findAll() {
        return jpaRepository.findAll();
    }
}
