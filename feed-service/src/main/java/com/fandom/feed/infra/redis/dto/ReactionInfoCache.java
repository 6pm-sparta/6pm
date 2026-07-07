package com.fandom.feed.infra.redis.dto;

public record ReactionInfoCache(long commentCount, long likeCount, boolean liked) {
    public static ReactionInfoCache of(long commentCount, long likeCount, boolean liked) {
        return new ReactionInfoCache(commentCount, likeCount, liked);
    }
}