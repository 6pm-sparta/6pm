package com.fandom.feed.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BroadcastPolicy {
    public static final long FANOUT_THRESHOLD = 10000;
    public static final int CHUNK_SIZE = 500;
}