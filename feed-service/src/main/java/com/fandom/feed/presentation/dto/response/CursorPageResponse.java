package com.fandom.feed.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record CursorPageResponse<T>(List<T> content, UUID nextCursor, boolean hasNext) {
    public static <T> CursorPageResponse<T> of(List<T> content, UUID nextCursor, boolean hasNext) {
        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }
}