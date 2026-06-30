package com.fandom.feed.presentation.dto.response;

import com.fandom.feed.infra.s3.dto.PresignedUrlInfo;

import java.util.List;

public record PresignedUrlResponse(List<PresignedUrlInfo> uploadUrls) {
    public static PresignedUrlResponse from(List<PresignedUrlInfo> uploadUrls) {
        return new PresignedUrlResponse(uploadUrls);
    }
}