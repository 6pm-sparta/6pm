package com.fandom.feed.application.policy;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum PostSort {
    LATEST, OLDEST;

    @JsonCreator
    public static PostSort from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(LATEST);
    }
}