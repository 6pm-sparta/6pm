package com.fandom.user_service.member.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowEventMessage(
        @JsonProperty("follow_id") UUID followId,
        @JsonProperty("follower_id") UUID followerId,
        @JsonProperty("followee_id") UUID followeeId,
        @JsonProperty("nickname") String nickname
) {
}
