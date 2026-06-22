package com.fandom.notification_service.infra.kafka;

public final class KafkaTopics {

    public static final String NOTIFICATION_SEND = "notification.send";
    public static final String NOTIFICATION_PUSH = "notification.push";
    public static final String NOTIFICATION_PUSH_FAILED = "notification.push.failed";

    public static final String USER_DELETED = "user.deleted";

    private KafkaTopics() {
    }
}
