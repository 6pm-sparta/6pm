package com.fandom.feed.infra.client.dto;

import java.util.UUID;

public record FollowingResponse(UUID authorId, boolean isLarge) {}