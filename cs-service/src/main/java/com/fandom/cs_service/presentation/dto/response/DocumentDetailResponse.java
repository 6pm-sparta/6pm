package com.fandom.cs_service.presentation.dto.response;

import com.fandom.cs_service.domain.entity.CsDocument;

import java.util.UUID;

public record DocumentDetailResponse(UUID id, String title, String content) {

    public static DocumentDetailResponse from(CsDocument document) {
        return new DocumentDetailResponse(document.getId(), document.getTitle(), document.getContent());
    }
}
