package com.fandom.feed.infra.client.dto;

import java.util.UUID;

public record UserResponse(UUID userId, String nickname) {}