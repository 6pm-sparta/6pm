package com.fandom.feed.domain.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImageUrlConverter {
    @Value("${s3.base-url}")
    private String baseUrl;

    public List<String> toImageUrls(List<String> keys) {
        return keys.stream().map(key -> baseUrl + key).toList();
    }
}