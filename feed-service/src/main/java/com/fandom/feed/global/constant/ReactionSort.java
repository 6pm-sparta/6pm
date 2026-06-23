package com.fandom.feed.global.constant;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum ReactionSort {
    LATEST, OLDEST;

    @JsonCreator
    public static ReactionSort from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(LATEST);
    }
}