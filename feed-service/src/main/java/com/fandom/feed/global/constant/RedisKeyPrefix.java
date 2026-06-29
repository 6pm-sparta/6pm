package com.fandom.feed.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyPrefix {
    public static final String POST_LIST = "feed:posts:";
    public static final String POST_LIST_ALL = POST_LIST + "all";
    public static final String POST_DETAIL = "feed:post:detail";

    public static final String COMMENT_COUNT = "feed:comment:count:";
    public static final String LIKE_SET = "feed:like:set:";
}