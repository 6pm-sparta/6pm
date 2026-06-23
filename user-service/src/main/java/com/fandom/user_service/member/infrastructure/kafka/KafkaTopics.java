package com.fandom.user_service.member.infrastructure.kafka;

public final class KafkaTopics {

    public static final String USER_MEMBER_WITHDRAWN = "user.member-withdrawn";
    public static final String USER_CREATOR_WITHDRAWN = "user.creator-withdrawn";
    public static final String USER_DELETED = "user.deleted";

    private KafkaTopics() {
    }
}
