package com.fandom.feed.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FeedPolicy {
    public static final int PAGE_SIZE = 20;
    public static final int MAX_CACHE_PAGE = 5;
    public static final int MAX_CACHE_SIZE = PAGE_SIZE * MAX_CACHE_PAGE;
}