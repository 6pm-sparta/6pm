package com.fandom.cs_service.application.port;

import java.util.List;
import java.util.UUID;


public interface CsVectorStorePort {

    // 문서 등록/갱신
    void save(UUID documentId, String title, String content);

    // 문서 삭제
    void delete(UUID documentId);

    // 질문과 유사한 문서 본문
    List<String> searchSimilar(String query, int topK);
}
