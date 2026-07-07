package com.fandom.order_service.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * notification.send 발행 payload.
 */
public record NotificationSendEvent(
        @JsonProperty("reference_id") UUID referenceId,
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("target_user_ids") List<UUID> targetUserIds
) {
}
