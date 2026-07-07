package com.fandom.cs_service.presentation.dto.response;

import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.entity.SenderRole;

import java.util.UUID;

public record CsMessageResponse(
        UUID id,
        SenderRole senderRole,
        String content
) {
    public static CsMessageResponse from(CsMessage m) {
        return new CsMessageResponse(m.getId(), m.getSenderRole(), m.getContent());
    }
}
