package com.fandom.feed.infra.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogContext {
    @SafeVarargs
    public static void with(Runnable task, Map.Entry<String, Object>... entries) {
        try {
            for (Map.Entry<String, Object> entry : entries)
                MDC.put(entry.getKey(), String.valueOf(entry.getValue()));
            task.run();
        } finally {
            for (Map.Entry<String, Object> entry : entries)
                MDC.remove(entry.getKey());
        }
    }

    @SafeVarargs
    public static void error(Throwable e, String message, Map.Entry<String, Object>... entries) {
        with(() -> log.error(message, e), entries);
    }

    @SafeVarargs
    public static void warn(String message, Map.Entry<String, Object>... entries) {
        with(() -> log.warn(message), entries);
    }

    @SafeVarargs
    public static void info(String message, Map.Entry<String, Object>... entries) {
        with(() -> log.info(message), entries);
    }
}