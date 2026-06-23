package com.fandom.user_service.member.infrastructure.kafka;

public final class KafkaTopics {

    public static final String USER_MEMBER_WITHDRAWN = "user.member-withdrawn";
    public static final String USER_CREATOR_WITHDRAWN = "user.creator-withdrawn";
    public static final String USER_DELETED = "user.deleted";
    public static final String USER_FOLLOWED = "user.followed";
    public static final String USER_UNFOLLOWED = "user.unfollowed";
    public static final String USER_CREATOR_CREATED = "user.creator-created";

    private KafkaTopics() {
    }
}
