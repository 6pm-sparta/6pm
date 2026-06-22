package com.fandom.feed.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorPageResponse<T>(List<T> content, UUID nextCursor, boolean hasMore) {
    public static <T> CursorPageResponse<T> of(List<T> content, UUID nextCursor, boolean hasMore) {
        return new CursorPageResponse<>(content, nextCursor, hasMore);
    }
}