package com.fandom.feed.application.policy;

public class PostPolicy {
    public static final int PAGE_SIZE = 20;
    public static final int MAX_CACHE_PAGE = 5;
    public static final int MAX_CACHE_SIZE = PAGE_SIZE * MAX_CACHE_PAGE; // 100
}