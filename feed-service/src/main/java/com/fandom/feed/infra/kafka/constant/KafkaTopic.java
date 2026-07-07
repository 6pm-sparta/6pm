package com.fandom.feed.infra.kafka.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopic {
    public static final String MEMBER_WITHDRAWN = "user.member-withdrawn";
    public static final String CREATOR_WITHDRAWN = "user.creator-withdrawn";
    public static final String NOTIFICATION_SEND = "notification.send";
}