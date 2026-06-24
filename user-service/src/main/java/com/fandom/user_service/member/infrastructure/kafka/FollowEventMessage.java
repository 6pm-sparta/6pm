package com.fandom.user_service.member.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record FollowEventMessage(
        @JsonProperty("follow_id") UUID followId,
        @JsonProperty("follower_id") UUID followerId,
        @JsonProperty("followee_id") UUID followeeId
) {
}
