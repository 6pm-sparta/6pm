package com.fandom.cs_service.application.service;

import com.fandom.cs_service.application.port.CsVectorStorePort;
import com.fandom.cs_service.domain.entity.CsDocument;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.cs_service.domain.repository.CsDocumentRepository;
import com.fandom.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class CsDocumentService {

    private final CsDocumentRepository documentRepository;
    private final CsVectorStorePort vectorStore;

    // 등록
    public CsDocument create(String title, String content) {
        CsDocument saved = documentRepository.save(
                CsDocument.builder().title(title).content(content).build());
        vectorStore.save(saved.getId(), title, content);
        log.info("cs 문서 등록 document_id={}", saved.getId());
        return saved;
    }

    // 목록
    @Transactional(readOnly = true)
    public Page<CsDocument> list(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }

    // 상세
    @Transactional(readOnly = true)
    public CsDocument get(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(CsErrorCode.CS_DOCUMENT_NOT_FOUND));
    }

    // 수정
    public CsDocument update(UUID documentId, String title, String content) {
        CsDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(CsErrorCode.CS_DOCUMENT_NOT_FOUND));
        document.updateContent(title, content);
        CsDocument saved = documentRepository.save(document);
        vectorStore.save(documentId, title, content);
        log.info("cs 문서 수정 document_id={}", documentId);
        return saved;
    }

    // 삭제
    public void delete(UUID documentId, UUID deletedBy) {
        CsDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(CsErrorCode.CS_DOCUMENT_NOT_FOUND));
        document.softDelete(deletedBy);
        documentRepository.save(document);
        vectorStore.delete(documentId);
        log.info("cs 문서 삭제 document_id={}", documentId);
    }
}
