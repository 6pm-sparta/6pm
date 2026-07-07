package com.fandom.feed.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryPolicy {
    public static final int MAX_RETRY_COUNT = 3;
}