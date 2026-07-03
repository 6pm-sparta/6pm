package com.fandom.cs_service.domain.repository;

import com.fandom.cs_service.domain.entity.CsDocument;

import java.util.List;

public interface CsDocumentRepository {

    CsDocument save(CsDocument document);

    List<CsDocument> findAll();
}
