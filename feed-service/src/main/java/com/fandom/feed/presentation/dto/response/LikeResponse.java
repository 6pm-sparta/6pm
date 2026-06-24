package com.fandom.feed.presentation.dto.response;

import java.util.UUID;

public record LikeResponse(UUID postId, long likeCount) {
    public static LikeResponse of(UUID postId, long likeCount) {
        return new LikeResponse(postId, likeCount);
    }
}