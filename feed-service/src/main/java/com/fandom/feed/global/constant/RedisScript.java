package com.fandom.feed.global.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisScript {
    public static final DefaultRedisScript<Long> DECREMENT_MIN_ZERO = new DefaultRedisScript<>(
            """
            local v = redis.call('DECR', KEYS[1])
            if v < 0 then redis.call('SET', KEYS[1], 0) end
            return v
            """,
            Long.class
    );
}