package com.fandom.cs_service.infra.ai;

import com.fandom.cs_service.application.port.CsVectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class PgVectorStoreAdapter implements CsVectorStorePort {

    private final VectorStore vectorStore;

    @Override
    public void save(UUID documentId, String title, String content) {
        String id = documentId.toString();
        vectorStore.delete(List.of(id));
        Document doc = new Document(id, content, Map.of(
                "csDocumentId", id,
                "title", title));
        vectorStore.add(List.of(doc));
        log.info("cs 문서 벡터 적재 document_id={}", id);
    }

    @Override
    public void delete(UUID documentId) {
        vectorStore.delete(List.of(documentId.toString()));
        log.info("cs 문서 벡터 삭제 document_id={}", documentId);
    }

    @Override
    public List<String> searchSimilar(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        if (results == null) {
            return List.of();
        }
        return results.stream().map(Document::getText).toList();
    }
}
