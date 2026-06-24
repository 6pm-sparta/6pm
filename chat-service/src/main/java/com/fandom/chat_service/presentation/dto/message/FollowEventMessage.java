package com.fandom.chat_service.presentation.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

// user.followed / user.unfollowed
public record FollowEventMessage(
        @JsonProperty("follow_id") UUID followId,
        @JsonProperty("follower_id") UUID followerId,
        @JsonProperty("followee_id") UUID followeeId
) {
}
