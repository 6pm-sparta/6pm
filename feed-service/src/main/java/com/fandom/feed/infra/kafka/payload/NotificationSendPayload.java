package com.fandom.feed.infra.kafka.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record NotificationSendPayload(
        @JsonProperty("reference_id") UUID referenceId,
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("target_user_ids") List<UUID> targetUserIds
) {}