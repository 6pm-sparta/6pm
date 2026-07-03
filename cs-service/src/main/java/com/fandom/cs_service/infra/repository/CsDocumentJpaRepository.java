package com.fandom.cs_service.infra.repository;

import com.fandom.cs_service.domain.entity.CsDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CsDocumentJpaRepository extends JpaRepository<CsDocument, UUID> {
}
