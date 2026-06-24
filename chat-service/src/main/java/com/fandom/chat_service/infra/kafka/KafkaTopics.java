package com.fandom.chat_service.infra.kafka;

public final class KafkaTopics {

    // user-service
    public static final String USER_CREATOR_CREATED = "user.creator-created";
    public static final String USER_FOLLOWED = "user.followed";
    public static final String USER_UNFOLLOWED = "user.unfollowed";
    public static final String USER_DELETED = "user.deleted";

    private KafkaTopics() {
    }
}
