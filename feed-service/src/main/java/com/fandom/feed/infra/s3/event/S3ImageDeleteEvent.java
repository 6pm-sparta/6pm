package com.fandom.feed.infra.s3.event;

import java.util.List;

public record S3ImageDeleteEvent(List<String> imageKeys) {}