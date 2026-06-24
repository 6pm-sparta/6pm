package com.fandom.chat_service.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record MessageListResponse(
        List<MessageResponse> messages,
        UUID nextCursor,
        boolean hasNext
) {
}
