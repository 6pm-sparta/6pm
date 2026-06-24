package com.fandom.user_service.follow.application.port;

import java.util.UUID;

public interface FollowEventPublisher {

    void publishFollowed(UUID followId, UUID followerId, UUID followeeId);

    void publishUnfollowed(UUID followId, UUID followerId, UUID followeeId);
}
