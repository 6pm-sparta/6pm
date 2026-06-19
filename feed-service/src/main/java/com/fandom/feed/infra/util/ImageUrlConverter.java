package com.fandom.feed.infra.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImageUrlConverter {
    @Value("${s3.base-url}")
    private String baseUrl;

    public String toImageUrl(String key) {
        return baseUrl + key;
    }

    public List<String> toImageUrls(List<String> keys) {
        return keys.stream().map(this::toImageUrl).toList();
    }
}