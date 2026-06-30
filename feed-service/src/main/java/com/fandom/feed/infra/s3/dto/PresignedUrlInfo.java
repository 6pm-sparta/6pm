package com.fandom.feed.infra.s3.dto;

public record PresignedUrlInfo(String imageName, String uploadUrl, String imageKey) {
    public static PresignedUrlInfo of(String imageName, String uploadUrl, String imageKey) {
        return new PresignedUrlInfo(imageName, uploadUrl, imageKey);
    }
}