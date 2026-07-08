package com.fandom.cs_service.presentation.dto.response;

import com.fandom.cs_service.domain.entity.CsDocument;

import java.util.UUID;

public record DocumentResponse(UUID id, String title) {

    public static DocumentResponse from(CsDocument document) {
        return new DocumentResponse(document.getId(), document.getTitle());
    }
}
