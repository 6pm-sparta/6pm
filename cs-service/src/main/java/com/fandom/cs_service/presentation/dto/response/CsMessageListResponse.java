package com.fandom.cs_service.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record CsMessageListResponse(
        List<CsMessageResponse> messages,
        UUID nextCursor,
        boolean hasNext
) {
}
