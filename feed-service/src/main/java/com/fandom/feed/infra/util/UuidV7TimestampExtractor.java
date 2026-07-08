package com.fandom.feed.infra.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UuidV7TimestampExtractor {
    public static long extract(UUID uuidV7) {
        return uuidV7.getMostSignificantBits() >>> 16;
    }
}