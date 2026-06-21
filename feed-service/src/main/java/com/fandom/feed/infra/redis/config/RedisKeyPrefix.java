package com.fandom.feed.infra.redis.config;

public final class RedisKeyPrefix {
    private RedisKeyPrefix() {}

    public static final String POST_LIST_LATEST = "feed:posts:latest";
    public static final String POST_LIST_OLDEST = "feed:posts:oldest";
    public static final String POST_DETAIL = "feed:post:detail";
    public static final String COMMENT_COUNT = "feed:comment:count";
    public static final String LIKE_SET = "feed:like:set";
}